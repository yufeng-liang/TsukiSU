package com.sukisu.ultra.data.column

import android.net.Uri
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.ui.util.ModuleProp
import com.sukisu.ultra.ui.util.flashModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** 单步日志的上限：够看清命令输出，又不至于把一次向导的全部输出都堆在内存/界面里。 */
private const val MAX_STEP_LOG_CHARS = 32 * 1024

/** `writeFile` / `writeConfig` 里唯一的变量占位符；其余内容一律按字面写入。 */
private const val KEYBOX_PLACEHOLDER = "\${keybox}"

/**
 * 专栏命令的安全边界。
 *
 * 命令来自索引仓（远程下发），界面上给用户看过原文才执行；这里再拦一道「明显不可逆」的模式，
 * 防止索引写错/被改之后，一次「跟着做」把设备送走。宽严的取舍：宁可拒绝一条其实无害的命令，
 * 也不放过 rm -rf / 这种一次就结束的。
 */
private val DANGEROUS_COMMAND_PATTERNS: List<Regex> = listOf(
    // 删除根目录：rm -rf /、rm -r /、rm -rf /*、su -c 'rm -rf /' 之类。
    // 末尾的边界条件让 /data、/data/foo 这类正常路径不被误伤。
    Regex("""\brm\s+(?:[^\n;&|]*\s)?["']?/+["']?(?:\s|$|\*|[;&|])"""),
    // 直接读块设备（dd if=/dev/block/... 会把分区原样读出来，配合 of= 就是写盘）。
    Regex("""\bdd\s+[^\n;&|]*\bif="""),
    // 格式化。
    Regex("""\bmkfs(?:\.[A-Za-z0-9]+)?\b"""),
    // 往块设备重定向。
    Regex("""(?:>|of=)\s*/dev/block"""),
    // 强制重启：绕过正常关机流程，可能丢数据。
    Regex("""\breboot\b[^\n;&|]*\s(?:-f|--force)\b"""),
)

/**
 * 专栏向导的步骤执行器。
 *
 * 设计约束（与契约文档一致）：
 * - **每一步都可解释**：命令原文、输出、失败原因都会写进 [ColumnStepResult.log]，用户能回看。
 * - **失败不抛出**：任何异常都收敛成 [ColumnStepStatus.FAILED] + 中文 message，向导才能继续
 *   给出「跳过 / 重试 / 回滚」的选择；协程取消除外，取消要继续往上抛。
 * - **改设备之前先记账**：写文件先备份再写，装模块成功后立刻记进回滚账本，保证「回滚」真的
 *   能撤销本次做过的事。
 * - **不替用户做决定**：`verify` / `reboot` / `askUser` 一律返回 NEEDS_USER，向导不会自动重启
 *   设备，也不会把自检结果当成「已经验证」。
 */
class ColumnStepRunnerImpl(
    private val fetcher: ColumnModuleFetcher,
    private val rollback: ColumnRollbackStore,
) : ColumnStepRunner {

    override suspend fun run(
        step: RepoColumnStep,
        topic: RepoColumnTopic,
        ctx: ColumnRunContext,
    ): ColumnStepResult = withContext(Dispatchers.IO) {
        if (!ctx.isActive()) {
            return@withContext ColumnStepResult(ColumnStepStatus.SKIPPED, "执行已取消")
        }

        val log = StepLog(ctx.log)
        try {
            when (val kind = step.kind.trim().lowercase()) {
                KIND_INSTALL_MODULE -> installModule(step, log)
                KIND_WRITE_CONFIG, KIND_WRITE_FILE -> writeFile(step, ctx, log)
                KIND_RUN_COMMAND -> runCommand(step, log)
                KIND_VERIFY -> verify(step)
                KIND_REBOOT -> reboot(step)
                KIND_ASK_USER -> askUser(step)
                else -> failed("未知步骤类型：${step.kind.ifBlank { "（空）" }}，本版本暂不支持")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 分支内部已经尽量把异常翻译成人话；走到这里说明还有漏网的（比如账本写失败），
            // 仍然要变成一次「步骤失败」，不能把整个向导带崩。
            failed("步骤执行异常：${e.message ?: e.javaClass.simpleName}", log)
        }
    }

    // ---------------------------------------------------------------------------------------
    // installModule
    // ---------------------------------------------------------------------------------------

    private suspend fun installModule(step: RepoColumnStep, log: StepLog): ColumnStepResult {
        if (step.source.kind.trim().isEmpty()) {
            return failed("这个步骤没有配置模块来源，无法安装", log)
        }

        // 幂等的第一道闸：索引里写了 moduleId 的，先按它查一次，省掉一次几十兆的下载。
        // 第二道在 resolveModuleId 之后（见下），防止索引写的 id 和 zip 里的对不上。
        val hintedId = step.source.moduleId.trim()
        if (hintedId.isNotEmpty() && isModuleInstalled(hintedId)) {
            log.line("模块 $hintedId 已安装，跳过")
            return skippedInstalled(hintedId, log)
        }

        val uri = fetcher.resolve(step).getOrElse { e ->
            return failed("准备模块失败：${e.message ?: e.javaClass.simpleName}", log)
        }
        if (uri.scheme != "file") {
            return failed("模块包地址不是本地文件：$uri", log)
        }
        log.line("已准备好模块包：${uri.lastPathSegment ?: uri}")

        // 先解析 id：装成功后 zip 会被 flashModule 删掉，那时就来不及读了。
        val moduleId = resolveModuleId(step, uri)
        log.line("模块 id：$moduleId")

        // 幂等的第二道闸：模块已经在设备上了就不再刷一遍。重装会覆盖用户在模块 WebUI 里
        // 做的配置，也会把「跟着做第二遍」变成一轮没必要的下载 + 刷写 + 重启。
        if (isModuleInstalled(moduleId)) {
            log.line("模块 $moduleId 已安装，跳过")
            return skippedInstalled(moduleId, log)
        }

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val result = flashModule(
            uri = uri,
            onStdout = { line ->
                stdout += line
                log.line(line)
            },
            onStderr = { line ->
                stderr += line
                log.line(line)
            },
            deleteSourceOnSuccess = true,
            archiveSource = false,
        )

        if (result.code != 0) {
            val detail = stderr.map { it.trim() }.filter { it.isNotEmpty() }
                .takeLast(3).joinToString("；")
                .ifBlank { result.err.trim() }
                .ifBlank { "ksud 退出码 ${result.code}" }
            return failed("模块安装失败：$detail", log)
        }
        log.line("模块安装成功：$moduleId")

        // 账本必须记上，否则回滚时这个模块会留在设备上。用 NonCancellable：用户在这一瞬间
        // 点了取消也不能让「装好了但没记账」发生。
        val recordError = withContext(NonCancellable) {
            runCatching { rollback.recordInstalledModule(moduleId) }.exceptionOrNull()
        }
        if (recordError != null) {
            return failed(
                "模块 $moduleId 已安装成功，但回滚账本没记上：${recordError.message ?: "未知错误"}。" +
                    "之后回滚时请手动卸载它。",
                log
            )
        }

        return ColumnStepResult(
            status = ColumnStepStatus.SUCCESS,
            message = "已安装模块：$moduleId",
            log = log.text(),
        )
    }

    /**
     * 尽力解析模块 id：先读 zip 里的 module.prop，再退到索引写的 moduleId，最后用文件名兜底。
     *
     * 回滚账本只拿 id 调 `ksud module uninstall`，所以「猜错」比「留空」更糟——这里每一层都
     * 只接受非空值。
     */
    private fun resolveModuleId(step: RepoColumnStep, uri: Uri): String {
        val file = uri.path?.let { File(it) }
        if (file != null && file.isFile) {
            ModuleProp.parseModuleProp(file)?.id?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        step.source.moduleId.trim().takeIf { it.isNotEmpty() }?.let { return it }
        return file?.name?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown-module"
    }

    /**
     * 模块是不是已经装在这台设备上。
     *
     * 用 `/data/adb/modules/<id>` 目录存在与否判断——这是 Magisk / KernelSU / APatch 共同的
     * 「已安装」标志。解析不出 id（`unknown-module`）时一律当作没装：宁可多装一次，也不能
     * 因为有一步猜错 id 就跳过该装的模块。
     */
    private fun isModuleInstalled(moduleId: String): Boolean {
        val id = moduleId.trim()
        if (id.isEmpty() || id == "unknown-module") return false
        return ColumnRootIo.isDirectory("/data/adb/modules/$id")
    }

    private fun skippedInstalled(moduleId: String, log: StepLog): ColumnStepResult =
        ColumnStepResult(
            status = ColumnStepStatus.SKIPPED,
            message = "模块 $moduleId 已经装了，跳过（要重装请先回滚）",
            log = log.text(),
        )

    // ---------------------------------------------------------------------------------------
    // writeFile / writeConfig
    // ---------------------------------------------------------------------------------------

    private suspend fun writeFile(
        step: RepoColumnStep,
        ctx: ColumnRunContext,
        log: StepLog,
    ): ColumnStepResult {
        val rawPath = step.filePath.trim()
        if (!rawPath.startsWith("/")) {
            return failed("写入失败：步骤没有给出 / 开头的绝对路径（filePath=${step.filePath.ifBlank { "空" }}）", log)
        }
        val path = try {
            ColumnRootIo.normalizeFilePath(rawPath)
        } catch (e: Exception) {
            return failed("写入失败：${e.message}", log)
        }

        if (step.fileContent.isEmpty()) {
            return failed("写入失败：步骤内容为空（$path）", log)
        }

        val content = if (step.fileContent.contains(KEYBOX_PLACEHOLDER)) {
            val keybox = ctx.keyboxContent?.takeIf { it.isNotBlank() }
                ?: return failed("需要密钥材料，但本次没有校验通过的密钥", log)
            step.fileContent.replace(KEYBOX_PLACEHOLDER, keybox)
        } else {
            step.fileContent
        }

        log.line("准备写入 $path（${content.toByteArray(Charsets.UTF_8).size} 字节）")

        // 幂等：内容已经一致就不要再写。重写会刷新 mtime，也可能触发依赖这个文件的模块
        // 重新生成配置；账本那边还会多一条没用的备份记录（虽然去重后不会真的多备份）。
        val current = runCatching { ColumnRootIo.readText(path) }.getOrNull()
        if (current == content) {
            log.line("内容已一致，跳过写入：$path")
            return ColumnStepResult(
                status = ColumnStepStatus.SKIPPED,
                message = "$path 内容已一致，无需重复写入",
                log = log.text(),
            )
        }

        return try {
            // 顺序不能反：先备份、再写。否则「写了但没备份」等于把用户原配置永久弄丢。
            rollback.recordWrittenFile(path)
            log.line("已记录回滚点：$path")

            ColumnRootIo.writeText(path, content)
            if (!ColumnRootIo.isFile(path)) {
                return failed("写入后校验失败：$path 不存在或不是普通文件", log)
            }
            val size = ColumnRootIo.size(path)
            if (size <= 0) {
                return failed("写入后校验失败：$path 大小为 $size 字节", log)
            }
            log.line("写入成功：$path（$size 字节）")
            ColumnStepResult(
                status = ColumnStepStatus.SUCCESS,
                message = "已写入 $path（$size 字节）",
                log = log.text(),
            )
        } catch (e: Exception) {
            failed("写入 $path 失败：${e.message ?: e.javaClass.simpleName}", log)
        }
    }

    // ---------------------------------------------------------------------------------------
    // runCommand
    // ---------------------------------------------------------------------------------------

    private fun runCommand(step: RepoColumnStep, log: StepLog): ColumnStepResult {
        val command = step.command.trim()
        if (command.isEmpty()) {
            return failed("命令为空，无法执行", log)
        }
        if (DANGEROUS_COMMAND_PATTERNS.any { it.containsMatchIn(command) }) {
            log.line("已拒绝：$command")
            return failed(
                "命令被安全策略拒绝：专栏命令不允许删除根目录、直接读写块设备、格式化或强制重启。" +
                    "如果你确认要执行，请自行在终端里运行。",
                log
            )
        }

        log.line("执行：$command")
        val outcome = ColumnRootIo.exec(command)
        val errors = outcome.err.map { it.trimEnd() }.filter { it.isNotEmpty() }
        val output = outcome.out.map { it.trimEnd() }.filter { it.isNotEmpty() }
        errors.forEach { log.line(it) }
        output.forEach { log.line(it) }

        if (!outcome.isSuccess) {
            val detail = (errors + output).takeLast(3).joinToString("；").ifBlank { "退出码非 0" }
            return failed("命令执行失败：$detail", log)
        }
        return ColumnStepResult(
            status = ColumnStepStatus.SUCCESS,
            message = "命令执行成功",
            log = log.text(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // 只提示、不动设备的步骤
    // ---------------------------------------------------------------------------------------

    private fun verify(step: RepoColumnStep): ColumnStepResult {
        val checkId = step.checkId.trim()
        if (checkId.isEmpty()) {
            val message = step.note.trim().ifBlank { "请按说明自行确认" }
            return ColumnStepResult(ColumnStepStatus.NEEDS_USER, message)
        }
        val hint = if (checkId in ColumnCheckIds.ALL) {
            "请在下方自检里确认：$checkId"
        } else {
            "请在下方自检里确认：$checkId（该自检项未登记，请按说明手动确认）"
        }
        return ColumnStepResult(ColumnStepStatus.NEEDS_USER, hint)
    }

    private fun reboot(step: RepoColumnStep): ColumnStepResult {
        val message = step.note.trim().ifBlank { "请手动重启设备（向导不会替您重启）。" }
        return ColumnStepResult(ColumnStepStatus.NEEDS_USER, message)
    }

    private fun askUser(step: RepoColumnStep): ColumnStepResult {
        val message = step.note.trim().ifBlank { "请确认后继续。" }
        return ColumnStepResult(ColumnStepStatus.NEEDS_USER, message)
    }

    private fun failed(message: String, log: StepLog? = null): ColumnStepResult =
        ColumnStepResult(ColumnStepStatus.FAILED, message, log?.text().orEmpty())

    /**
     * 单步日志：边产生边回调给界面，同时留一份给 [ColumnStepResult.log]。
     *
     * 回调（`ctx.log`）由界面提供，不能因为界面抛异常而中断安装流程；长度也有上限，避免一条
     * 输出很大的命令把内存撑起来。
     */
    private class StepLog(private val emit: (String) -> Unit) {
        private val buffer = StringBuilder()
        private var truncated = false

        fun line(text: String) {
            val line = text.trimEnd()
            if (line.isEmpty()) return
            runCatching { emit(line) }
            if (buffer.length >= MAX_STEP_LOG_CHARS) {
                if (!truncated) {
                    truncated = true
                    buffer.append("……（日志过长，已截断）\n")
                }
                return
            }
            buffer.append(line).append('\n')
        }

        fun text(): String = buffer.toString().trimEnd()
    }

    private companion object {
        const val KIND_INSTALL_MODULE = "installmodule"
        const val KIND_WRITE_CONFIG = "writeconfig"
        const val KIND_WRITE_FILE = "writefile"
        const val KIND_RUN_COMMAND = "runcommand"
        const val KIND_VERIFY = "verify"
        const val KIND_REBOOT = "reboot"
        const val KIND_ASK_USER = "askuser"
    }
}
