package com.sukisu.ultra.data.column

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.ModuleProp
import com.sukisu.ultra.ui.util.flashModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** 单步日志的上限：够看清命令输出，又不至于把一次向导的全部输出都堆在内存/界面里。 */
private const val MAX_STEP_LOG_CHARS = 32 * 1024

/**
 * 查上游版本的时限。
 *
 * 它只是「已装模块要不要提示升级」的前置查询，查不到就按跳过处理；为它把一步拖十几秒不值。
 */
private const val UPGRADE_CHECK_TIMEOUT_MS = 8_000L

/** `writeFile` / `writeConfig` 里唯一的变量占位符；其余内容一律按字面写入。 */
private const val KEYBOX_PLACEHOLDER = "\${keybox}"

/**
 * HMA-OSS 的配置目录通配。
 *
 * 服务端每次生成目录都带随机后缀（`/data/misc/hide_my_applist_<随机串>`），所以只能按前缀找。
 */
private const val HMA_DIR_GLOB = "/data/misc/hide_my_applist_*"

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
                KIND_INSTALL_MODULE -> installModule(step, ctx, log)
                KIND_INSTALL_APP -> installApp(step, ctx, log)
                KIND_WRITE_CONFIG, KIND_WRITE_FILE -> writeFile(step, ctx, log)
                KIND_WRITE_HMA_CONFIG -> writeHmaConfig(step, log)
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
    // installApp
    // ---------------------------------------------------------------------------------------

    /**
     * 下载一个 APK 并交给系统安装器。
     *
     * 商店**不代点安装**：只把包下载好、用 FileProvider 递给系统安装界面，确认按钮永远由用户按。
     * 这样既省掉「自己找地方下 APK」的步骤，又不会出现「点一下商店，后台静默装了个 App」。
     *
     * 幂等靠包名：APK 的包名只能从安装包本身读，已经在设备上的直接跳过——但 [ColumnRunContext.force]
     * 要放行，语义和 installModule 一致（用户点了「升级/重装」就该把安装器打开，否则永远停在跳过）。
     */
    private suspend fun installApp(
        step: RepoColumnStep,
        ctx: ColumnRunContext,
        log: StepLog,
    ): ColumnStepResult {
        if (step.source.kind.trim().isEmpty()) {
            return failed("这个步骤没有配置下载来源，无法安装", log)
        }

        val uri = fetcher.resolve(step).getOrElse { e ->
            return failed("准备安装包失败：${e.message ?: e.javaClass.simpleName}", log)
        }
        if (uri.scheme != "file") {
            return failed("安装包地址不是本地文件：$uri", log)
        }
        val file = uri.path?.let(::File)
        if (file == null || !file.isFile) {
            return failed("安装包不存在：${uri.lastPathSegment ?: uri}", log)
        }
        log.line("已下载安装包：${file.name}（${file.length() / 1024} KB）")

        val pm = ksuApp.packageManager
        @Suppress("DEPRECATION")
        val pkg = runCatching { pm.getPackageArchiveInfo(file.absolutePath, 0)?.packageName }
            .getOrNull()
            ?.trim()
            .orEmpty()
        if (pkg.isNotEmpty()) {
            log.line("包名：$pkg")
            val installed = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
            // force 语义与 installModule 一致：用户点了「升级/重做」就把安装器打开，不能永远停在跳过。
            if (installed && !ctx.force) {
                log.line("$pkg 已安装，跳过")
                return ColumnStepResult(
                    ColumnStepStatus.SKIPPED,
                    "$pkg 已经装了，跳过（要重装请在步骤上点升级/重做）",
                    log.text(),
                )
            }
            if (installed) log.line("$pkg 已安装，按用户要求重新打开安装器（覆盖安装）")
        } else {
            log.line("读不出包名，继续交给系统安装器判断")
        }

        val contentUri = try {
            FileProvider.getUriForFile(ksuApp, "${ksuApp.packageName}.fileprovider", file)
        } catch (e: Exception) {
            return failed("无法把安装包交给系统：${e.message ?: e.javaClass.simpleName}", log)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ksuApp.startActivity(intent)
        } catch (e: Exception) {
            return failed("打不开系统安装器：${e.message ?: e.javaClass.simpleName}", log)
        }

        // NEEDS_USER 而不是 SUCCESS：确认安装这一步不在商店手里，向导要停下等用户点完回来。
        return ColumnStepResult(
            ColumnStepStatus.NEEDS_USER,
            "已打开系统安装界面，请在弹窗里确认安装（装完回来说一声继续）",
            log.text(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // installModule
    // ---------------------------------------------------------------------------------------

    private suspend fun installModule(
        step: RepoColumnStep,
        ctx: ColumnRunContext,
        log: StepLog,
    ): ColumnStepResult {
        if (step.source.kind.trim().isEmpty()) {
            return failed("这个步骤没有配置模块来源，无法安装", log)
        }

        // 幂等的第一道闸：索引里写了 moduleId 的，先按它查一次，省掉一次几十兆的下载。
        // 第二道在 resolveModuleId 之后（见下），防止索引写的 id 和 zip 里的对不上。
        // 用户点了「升级」（ctx.force）时两道闸都要放行，否则升级永远走不到下载。
        // 两道闸都只认合法 id：columns.json 是远程数据，非法 id 连查都不查（它会被拼进 shell 路径）。
        val hintedId = step.source.moduleId.trim()
        if (hintedId.isNotEmpty() && !isValidColumnModuleId(hintedId)) {
            log.line("索引给的模块 id 不合法，已忽略：$hintedId")
        }
        if (!ctx.force && isValidColumnModuleId(hintedId) && isModuleInstalled(hintedId)) {
            return installedResult(step, hintedId, log)
        }

        val uriResult = fetcher.resolve(step)
        val resolveError = uriResult.exceptionOrNull()
        if (resolveError is ColumnCrossHostDownloadException) {
            return failed(crossHostMessage(resolveError), log)
        }
        val uri = uriResult.getOrElse { e ->
            return failed("准备模块失败：${e.message ?: e.javaClass.simpleName}", log)
        }
        if (uri.scheme != "file") {
            return failed("模块包地址不是本地文件：$uri", log)
        }
        log.line("已准备好模块包：${uri.lastPathSegment ?: uri}")

        // 先解析 id：装成功后 zip 会被 flashModule 删掉，那时就来不及读了。
        val resolvedId = resolveModuleId(step, uri, log)
        log.line("模块 id：$resolvedId")

        // 幂等的第二道闸：模块已经在设备上了就不再刷一遍。重装会覆盖用户在模块 WebUI 里
        // 做的配置，也会把「跟着做第二遍」变成一轮没必要的下载 + 刷写 + 重启。
        if (!ctx.force && isValidColumnModuleId(resolvedId) && isModuleInstalled(resolvedId)) {
            return installedResult(step, resolvedId, log)
        }
        if (ctx.force) log.line("按用户要求覆盖安装（升级/重做）")

        // 刷写前记下已有模块目录：刷完用它找出 ksud 真正装出来的 id。索引里的 id 只用来「提示」，
        // 一旦和 zip 里的对不上，按索引 id 记账会让两道幂等闸全部失效（每次重跑都重新下载刷写）。
        val modulesBefore = installedModuleDirs()

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
        // 回读真实 id：ksud 只认 zip 里的 module.prop，索引写的 id 可能对不上。对不上时若按索引 id
        // 记账，isModuleInstalled 永远查不到真正的目录，两道幂等闸全失效 → 每次重跑都重新下载刷写。
        val moduleId = detectInstalledModuleId(modulesBefore, resolvedId, log) ?: resolvedId
        if (!isValidColumnModuleId(moduleId)) {
            return failed(
                "模块装上了，但认不出它的真实模块 id（解析值：$resolvedId），没能记进回滚账本。" +
                    "请到「模块」页确认这次装的是什么，需要撤销时手动卸载。",
                log
            )
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
     * 回滚账本只拿 id 调 `ksud module uninstall`，所以「猜错」比「留空」更糟——每一层都只接受
     * **合法**的非空值（见 [isValidColumnModuleId]）。不合法的值不回落成危险值，统一记日志后继续
     * 往下找：索引是远程数据，一个带 `;` / `$( )` / 反引号的 id 就是任意 root 命令，绝不能让它
     * 流进账本、更不能流进 shell。
     */
    private fun resolveModuleId(step: RepoColumnStep, uri: Uri, log: StepLog): String {
        val file = uri.path?.let { File(it) }
        val fromProp = if (file != null && file.isFile) {
            ModuleProp.parseModuleProp(file)?.id?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        if (fromProp != null) {
            if (isValidColumnModuleId(fromProp)) return fromProp
            log.line("zip 里的模块 id 不合法，已忽略：$fromProp")
        }
        val fromIndex = step.source.moduleId.trim().takeIf { it.isNotEmpty() }
        if (fromIndex != null) {
            if (isValidColumnModuleId(fromIndex)) return fromIndex
            log.line("索引给的模块 id 不合法，已忽略：$fromIndex")
        }
        val fromName = file?.name?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
        if (fromName != null && isValidColumnModuleId(fromName)) return fromName
        return "unknown-module"
    }

    /**
     * 模块是不是已经装在这台设备上。
     *
     * 用 `/data/adb/modules/<id>` 目录存在与否判断——这是 Magisk / KernelSU / APatch 共同的
     * 「已安装」标志。解析不出 id（`unknown-module`）或 id 不合法时一律当作没装：宁可多装一次，
     * 也不能因为有一步猜错 id 就跳过该装的模块。
     */
    private fun isModuleInstalled(moduleId: String): Boolean {
        val id = moduleId.trim()
        if (id.isEmpty() || id == "unknown-module") return false
        if (!isValidColumnModuleId(id)) return false
        return ColumnRootIo.isDirectory("/data/adb/modules/$id")
    }

    /** `/data/adb/modules` 下现有的模块目录名（`ls -1` 不含隐藏项）。 */
    private fun installedModuleDirs(): Set<String> {
        val outcome = ColumnRootIo.exec("ls -1 /data/adb/modules 2>/dev/null")
        if (!outcome.isSuccess) return emptySet()
        return outcome.out.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * 用「刷写前后的目录差」找出 ksud 真正装出来的 id。
     *
     * 只认「恰好多了一个目录」这种明确情形；没多、多装都回落到索引解析值（调用方再决定怎么处理）。
     * 这一步的意义是：模块 id 以 zip 里的 `module.prop` 为准，索引写的只是提示。
     */
    private fun detectInstalledModuleId(before: Set<String>, expected: String, log: StepLog): String? {
        val added = installedModuleDirs() - before
        if (added.size != 1) {
            if (added.isEmpty()) {
                log.line("刷写后没看到新的模块目录，按解析出的 id 记账：$expected")
            } else {
                log.line("刷写后新增了多个模块目录（${added.joinToString("、")}），按解析出的 id 记账：$expected")
            }
            return null
        }
        val real = added.first()
        if (!real.equals(expected, ignoreCase = true)) {
            log.line("注意：zip 里实际的模块 id 是 $real（解析成了 $expected），已按实际 id 记账")
        }
        return real
    }

    /**
     * update.json 与 zip 不在同一主机、且索引没登记 sha256 时的说明。
     *
     * 选 FAILED 而不是 NEEDS_USER：向导把 NEEDS_USER 当成「已了结」，runAll 会继续往下走，
     * 用户会以为这一步装上了；失败才会停下来，并给出「让索引登记 sha256」的明确出路。
     */
    private fun crossHostMessage(e: ColumnCrossHostDownloadException): String =
        "update.json（${e.fromHost}）给出的下载地址指向了另一个主机（${e.toHost}），" +
            "而索引没有为这一步登记 sha256，无法确认下回来的是同一个包。" +
            "为安全起见没有下载、没有安装任何东西；如果确认可信，请让索引登记 sha256 后重试。"

    /**
     * 已装时给用户的结论。
     *
     * 能查到上游版本就比一比：有新版时把 [ColumnUpgrade] 带回去，界面据此给「升级」按钮（用户
     * 点了才会带 `force` 重跑）；查不到或已是最新就老实说跳过，不把联网失败变成步骤失败。
     */
    private suspend fun installedResult(
        step: RepoColumnStep,
        moduleId: String,
        log: StepLog,
    ): ColumnStepResult {
        val local = installedVersion(moduleId)
        val localText = local?.first?.takeIf { it.isNotEmpty() } ?: "未知版本"
        log.line("模块 $moduleId 已安装（$localText），检查上游版本")

        // 只吞「查不到」的常规异常；协程取消必须继续往上抛（runCatching 会把 CancellationException
        // 也吞掉，导致用户点了取消、这一步却当成「查完、按已装跳过」）。
        val remote = try {
            withTimeoutOrNull(UPGRADE_CHECK_TIMEOUT_MS) { fetcher.latestVersion(step) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.line("查询上游版本失败：${e.message ?: e.javaClass.simpleName}")
            null
        }
        if (remote == null) {
            log.line("拿不到上游版本，按已装跳过")
            return skippedInstalled(moduleId, log)
        }
        if (!isNewer(remote, local)) {
            log.line("已是最新（上游 ${remote.version}）")
            return skippedInstalled(moduleId, log)
        }

        log.line("发现新版：已装 $localText，上游 ${remote.version}")
        return ColumnStepResult(
            status = ColumnStepStatus.SKIPPED,
            message = "已装 $localText，上游最新 ${remote.version}，可以升级",
            log = log.text(),
            upgrade = ColumnUpgrade(
                moduleId = moduleId,
                installedVersion = localText,
                latestVersion = remote.version,
            ),
        )
    }

    private fun skippedInstalled(moduleId: String, log: StepLog): ColumnStepResult =
        ColumnStepResult(
            status = ColumnStepStatus.SKIPPED,
            message = "模块 $moduleId 已经装了，跳过（要重装请先回滚）",
            log = log.text(),
        )

    /** 读设备上已装模块的 `module.prop`，返回「版本名 to versionCode」；读不到给 null。 */
    private fun installedVersion(moduleId: String): Pair<String, Long>? {
        val text = runCatching {
            ColumnRootIo.readText("/data/adb/modules/$moduleId/module.prop")
        }.getOrNull() ?: return null
        var version = ""
        var code = 0L
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val index = line.indexOf('=')
            if (index <= 0) continue
            val value = line.substring(index + 1).trim()
            when (line.substring(0, index).trim()) {
                "version" -> version = value
                "versionCode" -> code = value.toLongOrNull() ?: 0L
            }
        }
        return if (version.isEmpty() && code <= 0L) null else version to code
    }

    /**
     * 上游是不是比已装的新。
     *
     * 两边都有数字 versionCode 就用它比（update.json 这类来源），否则按版本号里的数字段比
     * （`v1.5.2` vs `1.5.0`）。拿不准时判「不新」——升级会覆盖用户在模块 WebUI 里的配置，
     * 误报升级比漏报更烦人。
     */
    private fun isNewer(remote: UpstreamVersion, local: Pair<String, Long>?): Boolean {
        if (local == null) return false
        if (remote.versionCode > 0L && local.second > 0L) {
            return remote.versionCode > local.second
        }
        return compareVersions(remote.version, local.first) > 0
    }

    /** 按数字段比版本号：`v1.5.2` > `v1.5.0`，`oss-173` > `oss-172`。非数字段一律忽略。 */
    private fun compareVersions(a: String, b: String): Int {
        val left = versionParts(a)
        val right = versionParts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0L }
            val y = right.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun versionParts(raw: String): List<Long> =
        Regex("""\d+""").findAll(raw).map { it.value.toLongOrNull() ?: 0L }.toList()

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
        // 读之前先过「不是 /proc、不是超大文件、不是目录」：filePath 来自远程索引，指到 4 GiB 的镜像
        // 或 /proc/kmsg 时，base64 读法会把向导卡死/撑爆，而且写下去也没有意义。
        val current = try {
            ColumnRootIo.requireTextFileWithinLimit(path)
            ColumnRootIo.readText(path)
        } catch (e: IOException) {
            // 文件不存在（要新建）不算错误；存在但读不了/超限则是明确的拒绝。
            if (ColumnRootIo.exists(path) || ColumnRootIo.isSymlink(path)) {
                return failed("拒绝写入 $path：${e.message ?: "无法读取目标文件"}", log)
            }
            null
        }
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

    /**
     * 自动写入 HMA-OSS 的隐藏配置。
     *
     * 索引仓下发一份「配置包」（模板 + 目标应用 + 全局开关 + 要屏蔽的 hook），这里把它**合并**
     * 进设备上 HMA 已经生成的 `config.json`：
     * - `configVersion` 以设备上那份为准（HMA 自己写的，必然与当前版本一致）；版本不符 HMA 会
     *   静默不加载，等于什么都没做，所以绝不拿索引里的数字覆盖它。
     * - 已有模板、已配置的目标应用、全局开关一律保留用户原来的值，只补缺失项；目标应用已存在
     *   时只往它的 `applyTemplates` 里追加我们的模板名，不动其它开关。
     * - `disabledHooks` 按 className + methodName 去重后追加。
     *
     * 写完必须重启才生效（HMA 服务启动时才读文件），向导里已有 reboot 步骤。
     */
    private suspend fun writeHmaConfig(
        step: RepoColumnStep,
        log: StepLog,
    ): ColumnStepResult {
        if (step.fileContent.isBlank()) {
            return failed("HMA 配置为空：步骤没有给出配置内容", log)
        }
        val pack = try {
            JSONObject(step.fileContent)
        } catch (e: Exception) {
            return failed("HMA 配置包不是合法 JSON：${e.message ?: e.javaClass.simpleName}", log)
        }

        // 索引下发的配置包先按 HMA 的 schema 过一遍：少 argumentCount / isWhitelist 这类 HMA 要求
        // 必填的字段就补上默认值（并写日志），补不出来的（类型不对、appList 缺失）直接停手。
        // 不校验就写下去的后果是 HMA 整份配置解析失败被丢弃，用户的隐藏规则全挂，而向导报 SUCCESS。
        val normalizedPack = try {
            normalizeHmaPack(pack, log::line)
        } catch (e: HmaSchemaException) {
            return failed("HMA 配置包不符合 HMA-OSS 的 schema：${e.message}；未写入任何内容", log)
        }
        val templateName = normalizedPack.getString("templateName")

        val dirOutcome = ColumnRootIo.exec("ls -d $HMA_DIR_GLOB 2>/dev/null")
        val dir = dirOutcome.out.map { it.trim() }.firstOrNull { it.startsWith("/") }
            ?: return failed(
                "没找到 HMA-OSS 的配置目录（$HMA_DIR_GLOB）。请先装 HMA-OSS 并重启一次，" +
                    "让它生成 config.json 之后再来执行这一步。",
                log,
            )
        val configPath = "$dir/config.json"
        log.line("HMA 配置目录：$dir")

        // 必须有既有的 config.json：configVersion 只能由 HMA 自己写。向导猜一个版本号写进去，
        // HMA 在版本不符时会「Config version mismatch, need to reload」然后直接 return，
        // 整份配置不加载——隐藏全挂，而这一步还报成功。所以这里选择失败并要求用户先开一次 HMA。
        if (!ColumnRootIo.isFile(configPath)) {
            return failed(
                "设备上还没有 $configPath。请先打开一次 HMA-OSS（或重启设备）让它生成配置，再执行这一步；" +
                    "向导不会替你猜 configVersion。",
                log,
            )
        }
        val existingText = try {
            ColumnRootIo.readText(configPath)
        } catch (e: IOException) {
            // 读不出来（太大、在 /proc 下、不是普通文件）都停手，不要走到「新建一份」那条路上。
            return failed("读取 $configPath 失败：${e.message ?: e.javaClass.simpleName}", log)
        }
        if (existingText.isBlank()) {
            return failed("$configPath 是空文件（HMA 可能还没写完配置）。请先打开一次 HMA-OSS 再执行这一步。", log)
        }
        val existing = try {
            JSONObject(existingText)
        } catch (e: Exception) {
            // 文件坏了就停手：猜着覆盖等于把用户原来的隐藏规则一次性抹掉。
            return failed("$configPath 无法解析为 JSON，已停止（请先在 HMA 里导出配置确认文件完好）", log)
        }
        log.line("读取现有配置：${existingText.toByteArray(Charsets.UTF_8).size} 字节")

        // 设备上的这份也要校验：它已经不符合 schema 时（多半是别的工具写坏的），HMA 现在就没在生效，
        // 我们往里补东西只会让它丢得更彻底。停手让用户自己修。
        try {
            validateHmaConfig(existing, log::line, "设备上的 $configPath")
        } catch (e: HmaSchemaException) {
            return failed(
                "$configPath 本身就不符合 HMA-OSS 的 schema：${e.message}；已停止，未写入任何内容",
                log,
            )
        }
        if (!existing.has("configVersion")) {
            return failed(
                "$configPath 里没有 configVersion，HMA 无法判断配置版本；已停止，未写入任何内容。" +
                    "请先打开一次 HMA-OSS 让它重写配置。",
                log,
            )
        }

        val merged = try {
            mergeHmaConfig(existing, normalizedPack, log)
        } catch (e: HmaSchemaException) {
            return failed("合并 HMA 配置失败：${e.message}；未写入任何内容", log)
        }
        val mergedText = merged.toString()

        if (existingText.trim() == mergedText) {
            log.line("配置已包含本次内容，跳过写入")
            return ColumnStepResult(
                status = ColumnStepStatus.SKIPPED,
                message = "HMA 配置已经包含「$templateName」，无需重复写入",
                log = log.text(),
            )
        }

        return try {
            // 顺序不能反：先备份再写，否则覆盖失败时用户的隐藏规则就没了。
            rollback.recordWrittenFile(configPath)
            log.line("已记录回滚点：$configPath")

            ColumnRootIo.writeText(configPath, mergedText)
            if (!ColumnRootIo.isFile(configPath)) {
                return failed("写入后校验失败：$configPath 不存在或不是普通文件", log)
            }
            val size = ColumnRootIo.size(configPath)
            if (size <= 0) {
                return failed("写入后校验失败：$configPath 大小为 $size 字节", log)
            }
            log.line("写入成功：$configPath（$size 字节）")
            ColumnStepResult(
                status = ColumnStepStatus.SUCCESS,
                message = "已写入 HMA 配置（$size 字节），重启后生效",
                log = log.text(),
            )
        } catch (e: Exception) {
            failed("写入 HMA 配置失败：${e.message ?: e.javaClass.simpleName}", log)
        }
    }

    /**
     * 把配置包合并进设备现有的 HMA 配置，返回合并后的对象。
     *
     * 纯内存操作。[existing] 是调用方已经读过并校验过的 config.json（本函数不再处理「文件不存在」），
     * [pack] 必须是 [normalizeHmaPack] 的产物。合并策略是「只补不改」：用户已经有的模板、目标应用、
     * 全局开关全部保留，我们只加自己没有的那部分。结构上仍有冲突（类型不对、缺 configVersion）时抛
     * [HmaSchemaException]，由调用方翻译成失败——绝不「顺手覆盖成我们认为对的值」。
     */
    private fun mergeHmaConfig(
        existing: JSONObject,
        pack: JSONObject,
        log: StepLog,
    ): JSONObject {
        val out = JSONObject(existing.toString())

        // 1. configVersion：设备上那份说了算，绝不拿索引里的数字覆盖，也不猜。
        if (!out.has("configVersion")) {
            throw HmaSchemaException(
                "config.json 缺少 configVersion，无法在不猜版本号的前提下写入（版本猜错时 HMA 会丢弃整份配置）"
            )
        }

        // 2. 全局开关：只补缺失的，不覆盖用户改过的。
        pack.optJSONObject("globals")?.let { globals ->
            val keys = globals.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!out.has(key)) {
                    out.put(key, globals.get(key))
                    log.line("补全局开关：$key = ${globals.get(key)}")
                }
            }
        }

        // 3. 模板：同名模板已存在就保留用户那份。
        val templateName = pack.getString("templateName")
        val template = requireJsonObject(pack.get("template"), "template")
        run {
            val templates = out.optJSONObject("templates")
                ?: JSONObject().also { out.put("templates", it) }
            if (!templates.has(templateName)) {
                templates.put(templateName, template)
                log.line("新增模板：$templateName（${template.optJSONArray("appList")?.length() ?: 0} 个包名）")
            } else {
                log.line("模板已存在，保持用户原样：$templateName")
            }
        }

        // 4. 目标应用：新目标整段照搬；已存在的只补 applyTemplates。
        pack.optJSONObject("scope")?.let { packScope ->
            val scope = out.optJSONObject("scope") ?: JSONObject().also { out.put("scope", it) }
            val keys = packScope.keys()
            while (keys.hasNext()) {
                val target = keys.next()
                val incoming = packScope.optJSONObject(target) ?: continue
                val current = scope.optJSONObject(target)
                if (current == null) {
                    scope.put(target, incoming)
                    log.line("新增目标应用：$target")
                } else {
                    // 用户已经配过这个目标：保留他的开关，只保证我们的模板也应用上。
                    // 已有值类型不对时 optJSONArray 会给 null——那会被下面当成「没有」新建一个数组，
                    // 悄悄改掉用户的东西，所以这里显式检查。
                    if (current.has("applyTemplates") && current.optJSONArray("applyTemplates") == null) {
                        throw HmaSchemaException(
                            "scope[$target].applyTemplates 必须是字符串数组，实际是${jsonTypeName(current.get("applyTemplates"))}"
                        )
                    }
                    val applied = current.optJSONArray("applyTemplates")
                        ?: JSONArray().also { current.put("applyTemplates", it) }
                    if (!jsonArrayContains(applied, templateName)) {
                        applied.put(templateName)
                        log.line("目标 $target 已存在，追加模板：$templateName")
                    }
                }
            }
        }

        // 5. disabledHooks：按类名 + 方法名去重后追加。
        pack.optJSONArray("disabledHooks")?.let { hooks ->
            if (hooks.length() > 0) {
                val current = out.optJSONArray("disabledHooks")
                    ?: JSONArray().also { out.put("disabledHooks", it) }
                for (i in 0 until hooks.length()) {
                    val hook = hooks.optJSONObject(i) ?: continue
                    if (!jsonHookContains(current, hook)) {
                        current.put(hook)
                        log.line("新增 hook 屏蔽：${hook.optString("className")}#${hook.optString("methodName")}")
                    }
                }
            }
        }

        return out
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
        const val KIND_INSTALL_APP = "installapp"
        const val KIND_WRITE_CONFIG = "writeconfig"
        const val KIND_WRITE_FILE = "writefile"
        const val KIND_WRITE_HMA_CONFIG = "writehmaconfig"
        const val KIND_RUN_COMMAND = "runcommand"
        const val KIND_VERIFY = "verify"
        const val KIND_REBOOT = "reboot"
        const val KIND_ASK_USER = "askuser"
    }
}

/** `JSONArray` 里是否已有这个字符串（HMA 的 `applyTemplates` 是字符串数组）。 */
private fun jsonArrayContains(array: JSONArray, value: String): Boolean {
    for (i in 0 until array.length()) {
        if (array.optString(i) == value) return true
    }
    return false
}

/**
 * `disabledHooks` 里是否已有同一个 hook（按 className + methodName 判重，argumentCount 可不同）。
 */
private fun jsonHookContains(array: JSONArray, hook: JSONObject): Boolean {
    val className = hook.optString("className")
    val methodName = hook.optString("methodName")
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.optString("className") == className && item.optString("methodName") == methodName) return true
    }
    return false
}

// ── HMA-OSS 配置的 schema 校验 ────────────────────────────────────────────────
//
// 为什么要这么啰嗦：HMA-OSS 的 `common/.../JsonConfig.kt` 里
// `data class Template(val isWhitelist: Boolean, val appList: Set<String>)` 和
// `data class HookItem(val className: String, val methodName: String, val argumentCount: Int)`
// **所有字段都没有默认值**，而它解码用的 kotlinx Json 一旦遇到缺字段的对象就整个 `parse` 抛异常，
// `HMAService` 那边只打一行「Failed to parse config json, skip it」把**整份配置丢掉**。
// 索引契约（contrib/repo/README.md）只要求 hook 有 className + methodName——少一个 argumentCount
// 就会让用户的隐藏静默全挂，而向导报的是 SUCCESS。所以下面在写盘之前，按 HMA 的 schema
// 逐项校验、补缺省值，补不动的就停下来报错（报错必须点名是哪一项、缺什么）。

/** HMA 配置结构不符合 HMA-OSS 的解析要求。异常信息面向用户，必须能指出具体是哪一项。 */
private class HmaSchemaException(message: String) : Exception(message)

/** `AppConfig` 里的字符串数组字段（键名与 HMA-OSS `JsonConfig.AppConfig` 一致）。 */
private val HMA_APP_STRING_ARRAY_FIELDS = listOf(
    "applyTemplates",
    "applyPresets",
    "applySettingTemplates",
    "applySettingsPresets",
    "extraAppList",
    "extraOppositeAppList",
)

/** `AppConfig` 里的布尔字段。 */
private val HMA_APP_BOOLEAN_FIELDS = listOf(
    "useWhitelist",
    "excludeSystemApps",
    "hideInstallationSource",
    "hideSystemInstallationSource",
    "excludeTargetInstallationSource",
    "invertActivityLaunchProtection",
    "excludeVoldIsolation",
)

/** 类型名（中文），用于「这里期望数组、实际给了字符串」这类报错。 */
private fun jsonTypeName(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> "对象"
    is JSONArray -> "数组"
    is String -> "字符串"
    is Boolean -> "布尔值"
    is Number -> "数字"
    else -> value.javaClass.simpleName
}

private fun requireJsonObject(value: Any?, field: String): JSONObject =
    value as? JSONObject ?: throw HmaSchemaException("$field 必须是对象，实际是${jsonTypeName(value)}")

private fun requireJsonText(obj: JSONObject, key: String, field: String): String {
    val value = obj.opt(key)
    if (value == null || value == JSONObject.NULL) {
        throw HmaSchemaException("$field 缺少 $key（HMA 要求该字段必须存在）")
    }
    if (value !is String) {
        throw HmaSchemaException("$field.$key 必须是字符串，实际是${jsonTypeName(value)}")
    }
    if (value.isEmpty()) throw HmaSchemaException("$field.$key 不能是空字符串")
    return value
}

/** 校验字符串数组并去重（HMA 侧是 `Set<String>`，重复项只会静默合并，这里提前去掉）。 */
private fun requireStringArray(value: Any?, field: String): JSONArray {
    val array = value as? JSONArray ?: throw HmaSchemaException(
        "$field 必须是字符串数组，实际是${jsonTypeName(value)}"
    )
    val unique = LinkedHashSet<String>(array.length())
    for (i in 0 until array.length()) {
        val item = array.opt(i)
        if (item !is String) {
            throw HmaSchemaException("$field[$i] 必须是字符串，实际是${jsonTypeName(item)}")
        }
        unique.add(item)
    }
    val out = JSONArray()
    unique.forEach { out.put(it) }
    return out
}

/**
 * 规范化一条 `disabledHooks`：`className` / `methodName` 必须有，
 * `argumentCount` 缺失时补 **-1**（HMA 的界面里 -1 表示「任意参数个数」），类型不对就报错。
 */
private fun normalizeHook(value: Any?, index: Int, log: (String) -> Unit): JSONObject {
    val field = "disabledHooks[$index]"
    val hook = requireJsonObject(value, field)
    val className = requireJsonText(hook, "className", field)
    val methodName = requireJsonText(hook, "methodName", field)
    val argumentCount = when (val raw = hook.opt("argumentCount")) {
        null, JSONObject.NULL -> {
            log("$field 缺少 argumentCount，按 -1（任意参数个数）补上")
            -1
        }

        is Int -> raw
        is Long -> raw.toInt()
        is Double -> {
            if (raw % 1.0 != 0.0) throw HmaSchemaException("$field.argumentCount 必须是整数，实际是 $raw")
            raw.toInt()
        }

        else -> throw HmaSchemaException("$field.argumentCount 必须是整数，实际是${jsonTypeName(raw)}")
    }
    return JSONObject().apply {
        put("className", className)
        put("methodName", methodName)
        put("argumentCount", argumentCount)
    }
}

/**
 * 规范化一个模板：`isWhitelist` 缺失补 **true**（HMA 默认「隐藏名单」语义，与向导模板一致），
 * `appList` 必须有且是字符串数组。返回只含这两个键的对象——多余键 HMA 不认识，留着反而有风险。
 */
private fun normalizeTemplate(value: Any?, name: String, log: (String) -> Unit): JSONObject {
    val field = "templates[$name]"
    val template = requireJsonObject(value, field)
    val isWhitelist = when (val raw = template.opt("isWhitelist")) {
        null, JSONObject.NULL -> {
            log("模板 $name 缺少 isWhitelist，按 true（隐藏名单）补上")
            true
        }

        is Boolean -> raw
        else -> throw HmaSchemaException("$field.isWhitelist 必须是布尔值，实际是${jsonTypeName(raw)}")
    }
    val rawAppList = template.opt("appList")
        ?: throw HmaSchemaException("$field 缺少 appList（HMA 要求该字段必须存在）")
    val appList = requireStringArray(rawAppList, "$field.appList")
    return JSONObject().apply {
        put("isWhitelist", isWhitelist)
        put("appList", appList)
    }
}

/**
 * 校验 `scope` 里某个应用的配置。缺的字段可以缺（HMA 的 `AppConfig` 全都有默认值），
 * 但**存在且类型不对**就必须停手：那会让 HMA 整份配置解析失败。未知的键保留——
 * HMA 用 `ignoreUnknownKeys = true`，那是它自己的扩展字段，我们不该替它删。
 */
private fun validateAppConfig(value: Any?, target: String): JSONObject {
    val field = "scope[$target]"
    val app = requireJsonObject(value, field)
    HMA_APP_STRING_ARRAY_FIELDS.forEach { key ->
        if (app.has(key)) requireStringArray(app.get(key), "$field.$key")
    }
    HMA_APP_BOOLEAN_FIELDS.forEach { key ->
        if (app.has(key) && app.get(key) !is Boolean) {
            throw HmaSchemaException("$field.$key 必须是布尔值，实际是${jsonTypeName(app.get(key))}")
        }
    }
    if (app.has("restrictedZygotePermissions")) {
        val raw = app.get("restrictedZygotePermissions")
        val array = raw as? JSONArray
            ?: throw HmaSchemaException("$field.restrictedZygotePermissions 必须是整数数组，实际是${jsonTypeName(raw)}")
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item !is Int && item !is Long) {
                throw HmaSchemaException("$field.restrictedZygotePermissions[$i] 必须是整数，实际是${jsonTypeName(item)}")
            }
        }
    }
    return app
}

/**
 * 按 HMA 的 schema 校验并就地规范化一份 config.json（设备上已有的、或合并后的结果）。
 *
 * 校验不过直接抛 [HmaSchemaException]：这份文件是用户正在用的隐藏配置，格式已经不对时
 * 「再往里补」只会让 HMA 丢得更彻底，必须停手让人去修。
 */
private fun validateHmaConfig(config: JSONObject, log: (String) -> Unit, where: String) {
    (config.opt("templates") as? JSONObject)?.let { templates ->
        val normalized = JSONObject()
        val keys = templates.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            normalized.put(name, normalizeTemplate(templates.get(name), name, log))
        }
        config.put("templates", normalized)
    }
    config.opt("disabledHooks")?.let { raw ->
        val array = raw as? JSONArray
            ?: throw HmaSchemaException("$where 的 disabledHooks 必须是数组，实际是${jsonTypeName(raw)}")
        val normalized = JSONArray()
        for (i in 0 until array.length()) normalized.put(normalizeHook(array.get(i), i, log))
        config.put("disabledHooks", normalized)
    }
    (config.opt("scope") as? JSONObject)?.let { scope ->
        val keys = scope.keys()
        while (keys.hasNext()) {
            val target = keys.next()
            validateAppConfig(scope.get(target), target)
        }
    }
    if (config.has("detailLog") && config.get("detailLog") !is Boolean) {
        throw HmaSchemaException("$where 的 detailLog 必须是布尔值，实际是${jsonTypeName(config.get("detailLog"))}")
    }
}

/**
 * 校验索引给的「HMA 配置包」（`step.fileContent` 解析出来的对象）并返回规范化副本。
 *
 * 契约见 `contrib/repo/README.md`：必须有 `templateName` 与非空 `template.appList`；
 * `scope` 的值是对象；`disabledHooks` 每项要有 className + methodName。
 */
private fun normalizeHmaPack(pack: JSONObject, log: (String) -> Unit): JSONObject {
    val templateName = pack.opt("templateName")
    if (templateName !is String || templateName.isBlank()) {
        throw HmaSchemaException("配置包缺少 templateName（或不是非空字符串）")
    }
    val template = pack.opt("template")
        ?: throw HmaSchemaException("配置包缺少 template（模板内容）")
    val normalized = JSONObject()
    normalized.put("templateName", templateName)
    normalized.put("template", normalizeTemplate(template, templateName, log))

    pack.opt("globals")?.let { raw ->
        val globals = requireJsonObject(raw, "globals")
        val keys = globals.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // HMA 的全局项都是标量（Boolean / Int），给成对象或数组说明索引写错了层级。
            val value = globals.get(key)
            if (value is JSONObject || value is JSONArray || value == JSONObject.NULL) {
                throw HmaSchemaException("globals.$key 必须是布尔值或数字，实际是${jsonTypeName(value)}")
            }
        }
        normalized.put("globals", globals)
    }

    pack.opt("disabledHooks")?.let { raw ->
        val array = raw as? JSONArray
            ?: throw HmaSchemaException("配置包的 disabledHooks 必须是数组，实际是${jsonTypeName(raw)}")
        val hooks = JSONArray()
        for (i in 0 until array.length()) hooks.put(normalizeHook(array.get(i), i, log))
        normalized.put("disabledHooks", hooks)
    }

    pack.opt("scope")?.let { raw ->
        val scope = requireJsonObject(raw, "scope")
        val keys = scope.keys()
        while (keys.hasNext()) {
            val target = keys.next()
            validateAppConfig(scope.get(target), target)
        }
        normalized.put("scope", scope)
    }
    return normalized
}
