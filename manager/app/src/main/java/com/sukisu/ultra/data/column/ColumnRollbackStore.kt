package com.sukisu.ultra.data.column

import android.util.Base64
import android.util.Log
import com.sukisu.ultra.data.backup.sha256Hex
import com.sukisu.ultra.ui.util.getRootShell
import com.sukisu.ultra.ui.util.uninstallModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** 专栏向导的账本目录：和契约文档里的隐藏环境配置放在一起，卸载 app 也不会丢。 */
internal const val COLUMN_LEDGER_DIR = "/data/adb/.config/hide_env"

private const val LEDGER_FILE_NAME = "rollback.json"
private const val BACKUP_DIR_NAME = "backup"
private const val LEDGER_VERSION = 1

/** 写文件时每个 base64 分块的长度上限，避免单条命令太长被 shell 截断。 */
private const val WRITE_CHUNK_CHARS = 64 * 1024

private const val TAG = "ColumnRollback"

/**
 * 单次读取/备份的文本文件上限。
 *
 * 账本、HMA 配置、模块属性都是几十 KB 级别；1 MiB 已经非常宽松。没有上限时 `base64` 会把整个
 * 文件读进内存（4/3 倍放大），而指向 `/proc/kmsg` 这类节点的「文件」根本读不完——两者都能把向导
 * 卡死或 OOM。超过就拒绝这一步，让人去看看到底写了什么路径。
 */
internal const val MAX_TEXT_FILE_BYTES = 1L * 1024 * 1024

/** 新建文件的默认权限：只有 root 可读写。 */
private const val DEFAULT_NEW_FILE_MODE = "600"

/** 只接受八进制权限位；`stat`/索引给的值在拼进命令前必须先过这一关。 */
private val FILE_MODE_PATTERN = Regex("^[0-7]{3,4}$")

/** 伪文件系统：内容由内核动态生成，「文件大小」和「读完」都不成立，向导一律不碰。 */
private val PSEUDO_FS_PREFIXES = listOf("/proc", "/sys")

/**
 * 模块 id 是否合法。
 *
 * 与 ksud 自己的约束一致（`userspace/ksud/src/module.rs`：`^[a-zA-Z][a-zA-Z0-9._-]+$`），
 * 另外加一条长度上限 64。这不是「好看」的问题：module id 来自**远程索引**，而它会被拼进
 * `ksud module uninstall <id>` 这类 root 命令行；一个带 `;` / `$( )` / 反引号的 id 就是任意
 * root 命令。ksud 自己的正则挡不住，因为注入发生在 shell 层、进 ksud 之前。
 */
internal fun isValidColumnModuleId(value: String): Boolean {
    val id = value.trim()
    if (id.isEmpty() || id.length > 64) return false
    return id.matches(Regex("^[A-Za-z][A-Za-z0-9._-]*$"))
}

/** `/data/adb/.config/hide_env/rollback.json` 里的一条文件记录。 */
internal data class ColumnWrittenFile(
    val path: String,
    val existed: Boolean,
    val backup: String?,
)

/** 账本的内存形态；读写都走 org.json，不引额外序列化库。 */
internal data class ColumnRollbackLedger(
    val installedModules: MutableList<String> = mutableListOf(),
    val writtenFiles: MutableList<ColumnWrittenFile> = mutableListOf(),
) {
    val isEmpty: Boolean get() = installedModules.isEmpty() && writtenFiles.isEmpty()
}

/**
 * 以 root 身份做「账本 / 备份」需要的最小文件操作集合。
 *
 * 为什么不直接用 [java.io.File]：账本和备份都在 `/data/adb/.config/` 下，app 进程（u0_aXXX）
 * 没有权限，只有 root shell 能碰。所有路径都会先经过 [shellQuote]——路径来自索引和上游资产名，
 * 不能假设里面没有引号或空格。
 *
 * 单独抽成 object 是为了让 [ColumnStepRunnerImpl] 复用同一套「写文件」实现，避免第二份
 * base64 转义逻辑各自长歪。
 */
internal object ColumnRootIo {

    /** 单引号包裹，并把值里自带的单引号按 `'\''` 拆开。 */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** 一条 root 命令的结果；shell 本身起不来时也收敛成「失败 + 原因」，不往上抛。 */
    class Outcome(val isSuccess: Boolean, val out: List<String>, val err: List<String>) {
        /** 拼成一句话，用于错误信息里带上命令自己的输出。 */
        val message: String
            get() = (err + out).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("；")
    }

    fun exec(vararg commands: String): Outcome = try {
        val result = getRootShell().newJob().add(*commands).to(ArrayList(), null).exec()
        Outcome(result.isSuccess, result.out, result.err)
    } catch (e: Exception) {
        Outcome(false, emptyList(), listOf(e.message ?: e.javaClass.simpleName))
    }

    fun exists(path: String): Boolean = exec("test -e ${shellQuote(path)}").isSuccess

    fun isFile(path: String): Boolean = exec("test -f ${shellQuote(path)}").isSuccess

    fun isSymlink(path: String): Boolean = exec("test -L ${shellQuote(path)}").isSuccess

    fun isDirectory(path: String): Boolean = exec("test -d ${shellQuote(path)}").isSuccess

    /** 文件字节数；读不到返回 -1（调用方按「无法确认」处理，不要当成 0 字节）。 */
    fun size(path: String): Long {
        val outcome = exec("wc -c < ${shellQuote(path)}")
        if (!outcome.isSuccess) return -1
        return outcome.out.firstOrNull()?.trim()?.toLongOrNull() ?: -1
    }

    fun mkdirs(path: String): Boolean = exec("mkdir -p ${shellQuote(path)}").isSuccess

    fun delete(path: String): Boolean = exec("rm -f ${shellQuote(path)}").isSuccess

    fun copy(from: String, to: String): Boolean =
        exec("cp -p ${shellQuote(from)} ${shellQuote(to)}").isSuccess

    fun move(from: String, to: String): Boolean =
        exec("mv -f ${shellQuote(from)} ${shellQuote(to)}").isSuccess

    /**
     * 拒绝伪文件系统路径（`/proc`、`/sys`）。
     *
     * 这些路径下面的「文件」是内核现场生成的：`wc -c` 可能永远等不到 EOF，`base64` 更读不完。
     * 向导的读写目标只应该是真实存在的配置文件，碰这类路径一定是索引写错了。
     */
    fun requireNotPseudoFs(path: String) {
        val normalized = path.trim()
        PSEUDO_FS_PREFIXES.forEach { prefix ->
            if (normalized == prefix || normalized.startsWith("$prefix/")) {
                throw IOException("$path 在 $prefix 下，内容是内核动态生成的，向导不读写这类路径")
            }
        }
    }

    /**
     * 读之前先确认「这是一个体积可控的普通文件」。
     *
     * 顺序很重要：**先**判伪文件系统，**再**取大小。反过来的话，`wc -c < /proc/kmsg` 会直接卡住，
     * 连「拒绝」都走不到。路径不存在时直接返回（调用方要新建文件是正常情况）。
     */
    fun requireTextFileWithinLimit(path: String) {
        requireNotPseudoFs(path)
        if (!exists(path)) return
        if (isDirectory(path)) throw IOException("$path 是目录，不能按文本文件处理")
        val bytes = size(path)
        if (bytes < 0) {
            throw IOException("无法读取 $path 的大小，已拒绝（无法确认它是不是超大文件）")
        }
        if (bytes > MAX_TEXT_FILE_BYTES) {
            throw IOException(
                "$path 有 $bytes 字节，超过单文件 ${MAX_TEXT_FILE_BYTES / (1024 * 1024)} MiB 上限，已拒绝" +
                    "（避免把整份内容读进内存/备份）"
            )
        }
    }

    /** 目标文件当前的权限位（八进制字符串）；文件不存在或读不到时给 null。 */
    private fun fileMode(path: String): String? {
        val outcome = exec("stat -c %a ${shellQuote(path)} 2>/dev/null")
        if (!outcome.isSuccess) return null
        val mode = outcome.out.firstOrNull()?.trim().orEmpty()
        return mode.takeIf { FILE_MODE_PATTERN.matches(it) }
    }

    /** 把文件读成 UTF-8 文本；不存在、太大、在 /proc 下或读不出来都抛 [IOException]。 */
    fun readText(path: String): String {
        // 先卡体积与伪文件系统，再动 base64：这两类路径一旦开始读就没有「读完」这回事。
        requireTextFileWithinLimit(path)
        val quoted = shellQuote(path)
        if (!exec("test -f $quoted").isSuccess) throw IOException("文件不存在或不是普通文件：$path")
        // base64 输出里可能带换行（不同实现 wrap 宽度不同），先去掉再解码。
        val outcome = exec("base64 $quoted | tr -d '\\n'")
        if (!outcome.isSuccess) {
            throw IOException("读取文件失败：$path${outcome.message.takeIf { it.isNotEmpty() }?.let { "（$it）" } ?: ""}")
        }
        val encoded = outcome.out.joinToString("").trim()
        val bytes = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw IOException("读取文件失败：$path（返回内容不是合法 base64）", e)
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * 以 root 写文件：内容先 base64，再分块 `printf` 到临时文件，最后 `base64 -d` 落到目标路径。
     *
     * 不把内容直接拼进命令：配置里常有引号、换行、`$`、反斜杠，任何一处转义写错都会变成
     * 「命令跑了一半」这种最难查的失败。base64 只有字母数字和 `+/=`，对 shell 完全 inert。
     *
     * 写入是「临时文件 + mv」两步，目标文件要么是旧内容、要么是新内容，不会出现半截文件。
     */
    fun writeText(path: String, content: String) {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty() && !mkdirs(parent)) throw IOException("无法创建目录：$parent")

        // 权限：向导写的东西常常是敏感的（keybox 私钥、HMA 隐藏名单），新建文件一律 600（只 root 可读）。
        // 已存在的文件沿用原 mode——它可能是某个模块或系统组件按既有权限读取的配置，硬改成 600 会把
        // 「那边读不到」变成新的故障。读不到原 mode 时退回 600（保守：宁可自己看不了，不让别人看）。
        val mode = fileMode(path) ?: DEFAULT_NEW_FILE_MODE

        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) {
            // 空内容也要真的建出文件（后面的「大小 > 0」校验会拦住这种情况，但不该让写空文件本身失败）。
            if (!exec(": > ${shellQuote(path)} && chmod $mode ${shellQuote(path)}").isSuccess) {
                throw IOException("写入文件失败：$path")
            }
            return
        }

        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val base64Path = "$path.column-b64"
        val tempPath = "$path.column-tmp"
        try {
            encoded.chunked(WRITE_CHUNK_CHARS).forEachIndexed { index, chunk ->
                val redirect = if (index == 0) ">" else ">>"
                // chunk 里只有 base64 字符，单引号包裹足够；`%s` 是格式串，chunk 作为参数传入。
                val outcome = exec("printf '%s' ${shellQuote(chunk)} $redirect ${shellQuote(base64Path)}")
                if (!outcome.isSuccess) {
                    throw IOException("写入文件失败：$path（写临时数据时出错${outcome.message.takeIf { it.isNotEmpty() }?.let { "：$it" } ?: ""}）")
                }
            }
            if (!exec("base64 -d ${shellQuote(base64Path)} > ${shellQuote(tempPath)}").isSuccess) {
                throw IOException("写入文件失败：$path（base64 解码出错）")
            }
            if (!exec("chmod $mode ${shellQuote(tempPath)} && mv -f ${shellQuote(tempPath)} ${shellQuote(path)}").isSuccess) {
                throw IOException("写入文件失败：$path（无法替换目标文件）")
            }
        } finally {
            // 临时文件无论如何都不留在设备上；目标文件已经 mv 成功的话这里只剩空操作。
            delete(base64Path)
            delete(tempPath)
        }
    }

    /** 校验并规范化专栏步骤里的文件路径：必须是 `/` 开头的绝对路径，且不能含 `..`。 */
    fun normalizeFilePath(path: String): String {
        val trimmed = path.trim()
        if (!trimmed.startsWith("/")) {
            throw IOException("路径必须是 / 开头的绝对路径：$path")
        }
        if (trimmed.contains('\n') || trimmed.contains('\r')) {
            throw IOException("路径里不能包含换行：$path")
        }
        val normalized = trimmed.trimEnd('/')
        if (normalized.isEmpty() || normalized == "/") {
            throw IOException("路径不能是根目录：$path")
        }
        if (normalized.split('/').any { it == ".." }) {
            throw IOException("路径里不能包含 ..：$path")
        }
        return normalized
    }
}

/**
 * 专栏向导的回滚账本。
 *
 * 只记录「本次向导真的动过的东西」：装上的模块 id、写过的文件路径（以及写入前的备份）。
 * 回滚就是把这些改动逆序撤销，**不碰**用户原有的模块和配置。
 *
 * 账本放在 `/data/adb/.config/hide_env/rollback.json`，所有读写都经 root shell；文件内容
 * 损坏时不会「猜着回滚」——那可能删掉不该删的东西——而是拒绝执行并要求用户手动处理。
 */
class ColumnRollbackStore(
    private val rootDir: String = COLUMN_LEDGER_DIR,
) : ColumnRollback {

    private val ledgerPath = "$rootDir/$LEDGER_FILE_NAME"
    private val backupDir = "$rootDir/$BACKUP_DIR_NAME"

    /** 账本是「读-改-写」，同一时刻只允许一个操作，否则两次 record 会互相覆盖。 */
    private val mutex = Mutex()

    /**
     * 本进程里账本损坏后的归档路径；非空表示「账本已不可信」。
     *
     * 损坏的账本会被改名留档（见 [brokenLedger]），但**不能**就当成空账本继续用：上一次的记录一旦
     * 静默作废，用户点回滚只会得到「没有需要回滚的记录」，改动永久留在设备上。所以这里记住归档
     * 路径，在用户处理完（删掉归档文件、重启应用）之前，拒绝一切新的记账与回滚。
     */
    @Volatile
    private var quarantinedLedger: String? = null

    /** 隔离状态下拒绝记账；返回的异常会由执行器翻译成一次「步骤失败」，不会静默吞掉。 */
    private fun requireLedgerUsable() {
        val broken = quarantinedLedger ?: return
        throw IOException(quarantineMessage(broken, "损坏的账本已被隔离"))
    }

    /**
     * 找残留的损坏账本归档（`rollback.json.broken-*`）。
     *
     * 用于覆盖「上次损坏后应用被杀，这次重启后 [quarantinedLedger] 是空的」这种情况：账本文件不在、
     * 但归档还在，说明上一次的记录没被处理过，不能当成「没有记录」。
     */
    private fun leftoverArchive(): String? {
        val outcome = ColumnRootIo.exec("ls -1 $ledgerPath.broken-* 2>/dev/null")
        if (!outcome.isSuccess) return null
        return outcome.out.firstOrNull { it.trim().isNotEmpty() }?.trim()
    }

    private fun quarantineMessage(archivePath: String, reason: String): String =
        "回滚账本需要人工处理（$reason）：$archivePath。" +
            "在删除它并重启应用之前，向导不会记录新的回滚点、也不会执行回滚" +
            "（否则上一次的改动会被漏掉）。请检查该文件（必要时手动恢复其中的记录），确认处理完毕后删除它。"

    override suspend fun recordInstalledModule(moduleId: String): Unit = withContext(Dispatchers.IO) {
        requireLedgerUsable()
        val id = moduleId.trim()
        if (id.isEmpty()) throw IOException("模块 id 为空，无法记录回滚账本")
        // 账本里的 id 以后会被拼进 `ksud module uninstall <id>`：这里就挡住非法值，
        // 不让它进账本（也避免回滚时被当成 shell 片段执行）。
        if (!isValidColumnModuleId(id)) {
            throw IOException("模块 id 不合法（需匹配 ^[A-Za-z][A-Za-z0-9._-]{0,63}$）：$id，无法记账")
        }

        mutex.withLock {
            val ledger = load(strict = false)
            if (ledger.installedModules.any { it.equals(id, ignoreCase = true) }) return@withLock
            ledger.installedModules += id
            save(ledger)
        }
    }

    /**
     * 在写入某个文件**之前**调用：把原内容备份下来，并记一笔「本次向导要改这个文件」。
     *
     * 同一个路径只备份第一次：如果向导先写了一次、又写第二次，回滚时应该还原到「向导动手之前」
     * 的样子，而不是还原到第一次写的那一版。
     */
    override suspend fun recordWrittenFile(path: String): Unit = withContext(Dispatchers.IO) {
        requireLedgerUsable()
        val normalized = ColumnRootIo.normalizeFilePath(path)

        mutex.withLock {
            val ledger = load(strict = false)
            if (ledger.writtenFiles.any { it.path == normalized }) return@withLock

            // 备份前先卡「是普通文件、不是伪文件系统、不超过 1 MiB」：备份用 `cp -p`，目标是个
            // 读不完的节点或几十 GB 的镜像时，卡住的是 root shell，留的是一整份无用的副本。
            ColumnRootIo.requireTextFileWithinLimit(normalized)

            if (!ColumnRootIo.mkdirs(backupDir)) {
                throw IOException("无法创建备份目录：$backupDir")
            }

            val existed = ColumnRootIo.exists(normalized)
            var backup: String? = null
            if (existed) {
                // 符号链接不能当成普通文件备份：cp -p 还原时会顺着链接写进指向的真实文件，
                // 而 mv 覆盖又会把链接本身换成普通文件。两种结果都不是「还原原状」。
                if (ColumnRootIo.isSymlink(normalized)) {
                    throw IOException("$normalized 是符号链接，向导不写入符号链接，已拒绝")
                }
                if (!ColumnRootIo.isFile(normalized)) {
                    throw IOException("$normalized 不是普通文件，写入可能破坏系统内容，已拒绝")
                }
                val originalSize = ColumnRootIo.size(normalized)
                if (originalSize < 0) throw IOException("无法读取 $normalized 的大小，备份不可靠，已拒绝")

                // 路径的 sha256 前 16 位当备份名：单层、定长、不会因为路径里有 `/` 而变成子目录。
                val backupName = normalized.toByteArray(Charsets.UTF_8).sha256Hex().take(16)
                val backupPath = "$backupDir/$backupName"
                if (!ColumnRootIo.copy(normalized, backupPath)) {
                    throw IOException("无法备份 $normalized 到 $backupPath")
                }
                val backupSize = ColumnRootIo.size(backupPath)
                if (backupSize != originalSize) {
                    ColumnRootIo.delete(backupPath)
                    throw IOException("备份 $normalized 不完整（原 $originalSize 字节，备份 $backupSize 字节）")
                }
                backup = backupPath
            }

            ledger.writtenFiles += ColumnWrittenFile(
                path = normalized,
                existed = existed,
                backup = backup,
            )
            try {
                save(ledger)
            } catch (e: IOException) {
                // 账本没记上就别留下孤立备份：否则下次回滚会以为这个文件被向导改过。
                backup?.let { ColumnRootIo.delete(it) }
                throw e
            }
        }
    }

    /**
     * 逆序撤销账本里的全部改动，返回逐条中文结果。
     *
     * 顺序遵循契约 §4.5：先逆序卸载本次装的模块，再逆序处理文件（删除本次新建的、还原备份的）。
     * 单条失败不影响后续条目，失败项会写回账本，用户修好环境后可以再点一次回滚。
     */
    override suspend fun rollback(): List<String> = withContext(Dispatchers.IO) {
        requireLedgerUsable()
        mutex.withLock {
            val ledger = load(strict = true)
            if (ledger.isEmpty) return@withLock listOf("没有需要回滚的记录")

            val messages = mutableListOf<String>()
            val failedModules = mutableListOf<String>()
            val failedFiles = mutableListOf<ColumnWrittenFile>()

            ledger.installedModules.asReversed().forEach { moduleId ->
                // 账本里的 id 可能是旧版本写的、被人手改的、或者更早的漏洞版本从远程索引抄进来的。
                // 不合法的 id 绝不执行：它会被拼进 root 命令行（见 KsuCli.uninstallModule）。
                if (!isValidColumnModuleId(moduleId)) {
                    messages += "跳过非法的模块 id：$moduleId（未执行任何卸载命令；请手动检查该模块）"
                    return@forEach
                }
                val uninstalled = try {
                    // 这一层转义是「防御纵深」：KsuCli 那边仍然拼的是 `module uninstall <参数>`，
                    // 加上 shell 引号后，即使将来有人在 id 里塞了 `;`，也只会是一个普通参数。
                    uninstallModule(ColumnRootIo.shellQuote(moduleId))
                } catch (e: Exception) {
                    Log.w(TAG, "卸载模块失败：$moduleId", e)
                    false
                }
                if (uninstalled) {
                    messages += "已卸载模块：$moduleId"
                } else {
                    messages += "卸载模块失败：$moduleId（可稍后在模块页手动卸载）"
                    failedModules += moduleId
                }
            }

            ledger.writtenFiles.asReversed().forEach { entry ->
                val restored = try {
                    restoreFile(entry)
                } catch (e: Exception) {
                    Log.w(TAG, "还原文件失败：${entry.path}", e)
                    false to "还原文件失败：${entry.path}（${e.message ?: "未知错误"}）"
                }
                messages += restored.second
                if (!restored.first) failedFiles += entry
            }

            val remaining = ColumnRollbackLedger(
                installedModules = failedModules.asReversed().toMutableList(),
                writtenFiles = failedFiles.asReversed().toMutableList(),
            )
            try {
                save(remaining)
            } catch (e: IOException) {
                messages += "回滚账本更新失败：${e.message}（下次回滚可能重复处理已完成的条目）"
            }

            if (remaining.isEmpty && ledger.installedModules.isNotEmpty()) {
                messages += "模块卸载需要重启才彻底生效，建议重启设备。"
            }
            messages
        }
    }

    /** 处理一条文件记录；返回「是否成功 + 给用户看的一句话」。 */
    private fun restoreFile(entry: ColumnWrittenFile): Pair<Boolean, String> {
        val path = entry.path

        if (!entry.existed) {
            if (!ColumnRootIo.delete(path) || ColumnRootIo.exists(path)) {
                return false to "删除文件失败：$path（本次向导新建的文件还在，请手动检查）"
            }
            return true to "已删除本次新建的文件：$path"
        }

        val backup = entry.backup
        if (backup.isNullOrEmpty() || !ColumnRootIo.isFile(backup)) {
            return false to "缺少备份，无法还原：$path（记录里的备份：${backup ?: "无"}）"
        }
        val backupSize = ColumnRootIo.size(backup)
        if (!ColumnRootIo.copy(backup, path)) {
            return false to "还原文件失败：$path（备份仍保留在 $backup）"
        }
        val restoredSize = ColumnRootIo.size(path)
        if (backupSize >= 0 && restoredSize != backupSize) {
            return false to "还原文件失败：$path（还原后大小 $restoredSize 与备份 $backupSize 不一致，备份仍保留）"
        }
        ColumnRootIo.delete(backup)
        return true to "已还原原文件：$path"
    }

    /**
     * 读账本。
     *
     * 文件存在但读不出/解析不了 = 账本已损坏，**两种模式都抛异常**：
     * - [strict] = true（回滚）：损坏时继续回滚等于闭着眼睛删东西。
     * - strict = false（记账）：以前是「改名留档 + 当成空账本继续」，那是静默丢历史——上一次的记录
     *   一旦作废，用户点回滚只会得到「没有需要回滚的记录」，改动永久留在设备上。现在改成抛错并进入
     *   隔离状态（见 [quarantinedLedger]），由执行器翻译成一次可见的步骤失败。
     */
    private fun load(strict: Boolean): ColumnRollbackLedger {
        if (!ColumnRootIo.exists(ledgerPath)) {
            // 账本不在，但有归档：上一次跑的时候损坏过，用户还没处理。不能当成「没有记录」。
            val archived = leftoverArchive() ?: return ColumnRollbackLedger()
            quarantinedLedger = archived
            throw IOException(quarantineMessage(archived, "账本文件不存在"))
        }

        val text = try {
            ColumnRootIo.readText(ledgerPath)
        } catch (e: IOException) {
            return brokenLedger(strict, "读取失败：${e.message}")
        }
        if (text.isBlank()) {
            // 空文件基本只能是「上次写到一半断电」。回滚时不能把它当成「没东西可回滚」蒙混过去。
            return brokenLedger(strict, "文件为空（上次写入可能中断）")
        }

        return try {
            parseLedger(text)
        } catch (e: Exception) {
            brokenLedger(strict, "解析失败：${e.message}")
        }
    }

    private fun brokenLedger(strict: Boolean, reason: String): ColumnRollbackLedger {
        if (strict) {
            throw IOException(
                "回滚账本已损坏（$ledgerPath）：$reason。" +
                    "为避免误删，已停止回滚；请手动检查该文件，确认后删除再重试。"
            )
        }
        // 记账路径：把损坏的账本改名留档（内容还可能有用，不能直接覆盖或删除），然后同样抛错。
        // 抛错 + quarantinedLedger 的组合，保证「静默作废」变成「显式失败并且不再新增记录」。
        val brokenPath = "$ledgerPath.broken-${System.currentTimeMillis()}"
        val archived = ColumnRootIo.move(ledgerPath, brokenPath)
        val keepPath = if (archived) brokenPath else ledgerPath
        quarantinedLedger = keepPath
        val detail = if (archived) "已归档到 $brokenPath" else "归档失败（原文件仍在 $ledgerPath）"
        Log.w(TAG, "回滚账本损坏（$reason），$detail，已停止本次执行并进入隔离状态")
        throw IOException(
            "回滚账本已损坏（$reason），$detail。上一次的记录不可信，已停止记账与回滚：" +
                "请先检查 $keepPath（必要时手动恢复其中的记录），确认处理完毕后删除它并重启应用再继续。"
        )
    }

    private fun parseLedger(text: String): ColumnRollbackLedger {
        val root = JSONObject(text)
        val ledger = ColumnRollbackLedger()

        root.optJSONArray("installedModules")?.let { array ->
            for (i in 0 until array.length()) {
                val id = array.optString(i).trim()
                if (id.isNotEmpty() && ledger.installedModules.none { it.equals(id, ignoreCase = true) }) {
                    ledger.installedModules += id
                }
            }
        }

        root.optJSONArray("writtenFiles")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val path = item.optString("path").trim()
                if (path.isEmpty()) continue
                val backup = item.optString("backup").trim().ifEmpty { null }
                ledger.writtenFiles += ColumnWrittenFile(
                    path = path,
                    existed = item.optBoolean("existed", true),
                    backup = backup,
                )
            }
        }
        return ledger
    }

    private fun save(ledger: ColumnRollbackLedger) {
        if (ledger.isEmpty) {
            ColumnRootIo.delete(ledgerPath)
            return
        }
        if (!ColumnRootIo.mkdirs(rootDir)) throw IOException("无法创建账本目录：$rootDir")

        val root = JSONObject().apply {
            put("version", LEDGER_VERSION)
            put("installedModules", JSONArray(ledger.installedModules))
            put("writtenFiles", JSONArray().apply {
                ledger.writtenFiles.forEach { entry ->
                    put(JSONObject().apply {
                        put("path", entry.path)
                        put("existed", entry.existed)
                        entry.backup?.let { put("backup", it) }
                    })
                }
            })
        }
        // writeText 内部是「临时文件 + mv」，账本不会出现半截 JSON。
        ColumnRootIo.writeText(ledgerPath, root.toString(2) + "\n")
    }
}
