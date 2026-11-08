package com.sukisu.ultra.data.column

import com.sukisu.ultra.ui.util.withNewRootShell
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

/**
 * 专栏自检的默认实现：判据全部来自设备当前状态，只用 root shell 读，绝不写设备。
 *
 * 三条约定：
 * - 一轮自检共用一条 root shell。9 项检查要跑十来条命令，每条都新建 shell 就是十几次 su 握手，
 *   低端机上能明显看出卡顿；libsu 的 shell 本身串行执行命令，顺序跑不会互相踩。
 * - 单项失败不拖垮整轮。命令没权限、文件不存在、输出格式不对，都收敛成这一项 false + 一句中文
 *   原因，保证调用方永远拿到与入参等长、同顺序的结果。
 * - detail 是写给用户看的一句话，不是日志：说清「检出了什么」，需要用户动手时直接带上建议。
 */
class ColumnSelfCheckRunnerImpl : ColumnSelfCheckRunner {

    override suspend fun check(ids: List<String>): List<ColumnCheckResult> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            withNewRootShell {
                val shell = this
                ids.map { id ->
                    try {
                        checkOne(shell, id)
                    } catch (e: Exception) {
                        // 自检是「报告现状」，不是「必须成功」：单条命令抛异常也要给出结果，
                        // 否则界面上会缺一行，用户会以为漏检了。
                        ColumnCheckResult(id, false, "自检失败：${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        }

    private fun checkOne(shell: Shell, id: String): ColumnCheckResult = when (id) {
        ColumnCheckIds.ROOT_MANAGER -> checkRootManager(shell)
        ColumnCheckIds.ZYGISK_NEXT -> checkZygiskNext(shell)
        ColumnCheckIds.LSPOSED -> checkLsposed(shell)
        ColumnCheckIds.HMA_OSS -> checkHmaOss(shell)
        ColumnCheckIds.TEESIMULATOR -> checkTeeSimulator(shell)
        ColumnCheckIds.PIF -> checkPif(shell)
        ColumnCheckIds.FUSE_FIXER -> checkFuseFixer(shell)
        ColumnCheckIds.TRICKY_STORE -> checkTrickyStoreInstalled(shell)
        ColumnCheckIds.HYBRID_MOUNT -> checkHybridMount(shell)
        ColumnCheckIds.MOUNT_HIDE -> checkMountHide(shell)
        ColumnCheckIds.HMA_CONFIG -> checkHmaConfig(shell)
        ColumnCheckIds.DENYLIST -> checkDenylistPolicy(shell)
        ColumnCheckIds.PATHMASK -> checkPathMask(shell)
        ColumnCheckIds.TARGET_LIST -> checkTargetList(shell)
        ColumnCheckIds.KEYBOX -> checkKeybox(shell)
        ColumnCheckIds.ADB -> checkAdb(shell)
        else -> ColumnCheckResult(id, false, "未知自检项")
    }

    /** 1. 谁在管 root：三个目录哪个存在算哪个，优先 KernelSU/Magisk 的官方布局。 */
    private fun checkRootManager(shell: Shell): ColumnCheckResult {
        val dirs = shell.execCmd(
            "for d in /data/adb/magisk /data/adb/ksu /data/adb/ap; do [ -d \"\$d\" ] && echo \"\$d\"; done"
        ).nonBlankLines
        val dir = dirs.firstOrNull()
            ?: return failed(ColumnCheckIds.ROOT_MANAGER, "未检测到 /data/adb/magisk、/data/adb/ksu、/data/adb/ap")
        val name = when (dir) {
            "/data/adb/magisk" -> "Magisk"
            "/data/adb/ksu" -> "KernelSU"
            "/data/adb/ap" -> "APatch"
            else -> dir
        }
        return passed(ColumnCheckIds.ROOT_MANAGER, "已检测到 $name（$dir）")
    }

    /** 2. ZygiskNext：模块目录存在即可，能读到 module.prop 的 version 就带上。 */
    private fun checkZygiskNext(shell: Shell): ColumnCheckResult {
        val lines = shell.execCmd(
            "for d in /data/adb/modules/zygisksu /data/adb/modules/zygisk_next; do " +
                "if [ -d \"\$d\" ]; then echo \"dir=\$d\"; " +
                "if [ -f \"\$d/module.prop\" ]; then sed -n 's/^version=//p' \"\$d/module.prop\" | head -n 1; fi; " +
                "fi; done"
        ).nonBlankLines
        val dir = lines.firstOrNull { it.startsWith("dir=") }?.removePrefix("dir=")
            ?: return failed(
                ColumnCheckIds.ZYGISK_NEXT,
                "未检测到 /data/adb/modules/zygisksu 或 /data/adb/modules/zygisk_next"
            )
        // module.prop 的 version 常写成 v1.5.0；去掉前导 v 只为了 detail 读起来顺，不参与任何判断。
        val version = lines.firstOrNull { !it.startsWith("dir=") }?.trim().orEmpty()
            .trimStart('v', 'V')
        return if (version.isEmpty()) {
            passed(ColumnCheckIds.ZYGISK_NEXT, "已安装（ZygiskNext，未读到版本号）")
        } else {
            passed(ColumnCheckIds.ZYGISK_NEXT, "已安装（ZygiskNext $version）")
        }
    }

    /**
     * 模块安装检测的公共逻辑：probe 输出候选模块目录，取第一个存在目录作为结果。
     * 目录存在即算安装；有 module.prop 时顺带读 version，读不到也不影响「已安装」判定。
     */
    private fun checkModuleByProbe(
        shell: Shell,
        id: String,
        label: String,
        probe: String,
        missing: String,
    ): ColumnCheckResult {
        val dir = shell.execCmd(probe).nonBlankLines.firstOrNull()
            ?: return failed(id, missing)
        val version = shell.execCmd(
            "if [ -f \"$dir/module.prop\" ]; then sed -n 's/^version=//p' \"$dir/module.prop\" | head -n 1; fi"
        ).text.trim().trimStart('v', 'V')
        return if (version.isEmpty()) {
            passed(id, "已安装（$label，未读到版本号）")
        } else {
            passed(id, "已安装（$label $version）")
        }
    }

    /** LSPosed：Zygisk 版和经典版两个目录任一存在即可。 */
    private fun checkLsposed(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.LSPOSED,
        label = "LSPosed",
        probe = "for d in /data/adb/modules/zygisk_lsposed /data/adb/modules/lsposed; " +
            "do [ -d \"\$d\" ] && echo \"\$d\"; done",
        missing = "未检测到 /data/adb/modules/zygisk_lsposed 或 /data/adb/modules/lsposed",
    )

    /** HMA-OSS：固定目录或名字含 hma_oss 的模块目录任一存在即可。 */
    private fun checkHmaOss(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.HMA_OSS,
        label = "HMA-OSS",
        probe = "for d in /data/adb/modules/*; do [ -d \"\$d\" ] || continue; " +
            "b=\${d##*/}; case \"\$b\" in *[Hh][Mm][Aa]_[Oo][Ss][Ss]*) echo \"\$d\";; esac; done",
        missing = "未检测到名字含 hma_oss 的模块目录",
    )

    /** TeeSimulator：模块目录或 /data/adb/teesim 任一存在即可。 */
    private fun checkTeeSimulator(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.TEESIMULATOR,
        label = "TeeSimulator",
        probe = "for d in /data/adb/modules/teesim /data/adb/teesim; " +
            "do [ -d \"\$d\" ] && echo \"\$d\"; done",
        missing = "未检测到 /data/adb/modules/teesim 或 /data/adb/teesim",
    )

    /** Play Integrity Fix：两个常见模块目录任一存在即可。 */
    private fun checkPif(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.PIF,
        label = "PIF",
        probe = "for d in /data/adb/modules/playintegrityfix /data/adb/modules/playintegrityfork; " +
            "do [ -d \"\$d\" ] && echo \"\$d\"; done",
        missing = "未检测到 /data/adb/modules/playintegrityfix 或 /data/adb/modules/playintegrityfork",
    )

    /** FuseFixer：模块目录名含 fusefixer / fuse_fixer（大小写不敏感）即可。 */
    private fun checkFuseFixer(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.FUSE_FIXER,
        label = "FuseFixer",
        probe = "for d in /data/adb/modules/*; do [ -d \"\$d\" ] || continue; " +
            "b=\${d##*/}; case \"\$b\" in *[Ff][Uu][Ss][Ee]*[Ff][Ii][Xx][Ee][Rr]*) echo \"\$d\";; esac; done",
        missing = "未检测到名字含 fusefixer / fuse_fixer 的模块目录",
    )

    /** Tricky Store 安装状态（旧方案）：只表示装没装，target.txt 由 target-list 单独检查。 */
    private fun checkTrickyStoreInstalled(shell: Shell): ColumnCheckResult = checkModuleByProbe(
        shell = shell,
        id = ColumnCheckIds.TRICKY_STORE,
        label = "Tricky Store",
        probe = "for d in /data/adb/modules/tricky_store /data/adb/tricky_store; " +
            "do [ -d \"\$d\" ] && echo \"\$d\"; done",
        missing = "未检测到 /data/adb/modules/tricky_store 或 /data/adb/tricky_store",
    )

    /** 3. Hybrid Mount：模块目录存在即可。 */
    private fun checkHybridMount(shell: Shell): ColumnCheckResult {
        val out = shell.execCmd("[ -d /data/adb/modules/hybrid_mount ] && echo yes").text
        return if (out == "yes") {
            passed(ColumnCheckIds.HYBRID_MOUNT, "已安装 Hybrid Mount（/data/adb/modules/hybrid_mount）")
        } else {
            failed(ColumnCheckIds.HYBRID_MOUNT, "未检测到 /data/adb/modules/hybrid_mount")
        }
    }

    /** 4. HMA：配置写在 /data/misc 下，目录名带 hide_my_applist。 */
    private fun checkHmaConfig(shell: Shell): ColumnCheckResult {
        // HMA 实际把配置放在 /data/misc/<随机串>/ 里，只按字面 glob（/data/misc/*hide_my_applist*）
        // 在真机上会全部漏掉，所以往下多探一层；两种深度任一命中都算配置已写入。
        val paths = shell.execCmd(
            "ls -d /data/misc/*hide_my_applist* /data/misc/*/*hide_my_applist* 2>/dev/null"
        ).nonBlankLines
        val first = paths.firstOrNull()
            ?: return failed(ColumnCheckIds.HMA_CONFIG, "未在 /data/misc 下找到 hide_my_applist 配置目录")
        return passed(ColumnCheckIds.HMA_CONFIG, "已写入配置（$first）")
    }

    /** 5. denylist 策略：文件存在且内容非空；2 表示「仅还原挂载」。 */
    private fun checkDenylistPolicy(shell: Shell): ColumnCheckResult {
        val out = shell.execCmd(
            "if [ -e /data/adb/zygisksu/denylist_enforce ]; then echo EXISTS; " +
                "cat /data/adb/zygisksu/denylist_enforce; else echo MISSING; fi"
        )
        if (out.line(0) != "EXISTS") {
            return failed(
                ColumnCheckIds.DENYLIST,
                "未检测到 /data/adb/zygisksu/denylist_enforce（ZygiskNext 未写入 denylist 策略）"
            )
        }
        // 正常是单行数字；空文件或只有空行都视为「策略没生效」。
        val value = out.lines.drop(1).map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return failed(ColumnCheckIds.DENYLIST, "denylist_enforce 内容为空，策略未生效")
        return when (value) {
            "2" -> passed(ColumnCheckIds.DENYLIST, "已启用「仅还原挂载」策略（denylist_enforce = 2）")
            // 0 是 ZygiskNext 的「关闭」档：文件在、排除策略却没生效，判过等于骗用户。
            "0" -> failed(ColumnCheckIds.DENYLIST, "denylist_enforce = 0，排除策略未启用")
            else -> passed(ColumnCheckIds.DENYLIST, "已启用（denylist_enforce = $value）")
        }
    }

    /** 6. PathMask：/data/adb/modules 下有名字含 pathmask 的目录。 */
    private fun checkPathMask(shell: Shell): ColumnCheckResult {
        // 模块目录名大小写不固定（pathmask / PathMask 都见过），用字符类让 sh 自己忽略大小写。
        val dirs = shell.execCmd(
            "ls -d /data/adb/modules/*[Pp][Aa][Tt][Hh][Mm][Aa][Ss][Kk]* 2>/dev/null"
        ).nonBlankLines
        val first = dirs.firstOrNull()
            ?: return failed(ColumnCheckIds.PATHMASK, "未检测到名字含 pathmask 的模块目录")
        return passed(ColumnCheckIds.PATHMASK, "已安装（${first.substringAfterLast('/')}）")
    }

    /**
     * mount 隐藏方案：Hybrid Mount 与 pathmask 是同类，任一存在即可。
     * 两者都没有也不代表一定不安全——不少银行只看 root 管理器和 denylist，
     * 所以这一项只作提示，不拦流程。
     */
    private fun checkMountHide(shell: Shell): ColumnCheckResult {
        val hybrid = shell.execCmd("[ -d /data/adb/modules/hybrid_mount ] && echo yes").text.trim()
        val pathmaskDirs = shell.execCmd(
            "ls -d /data/adb/modules/*[Pp][Aa][Tt][Hh][Mm][Aa][Ss][Kk]* 2>/dev/null"
        ).nonBlankLines
        return when {
            hybrid == "yes" -> passed(ColumnCheckIds.MOUNT_HIDE, "已安装 Hybrid Mount")
            pathmaskDirs.isNotEmpty() ->
                passed(ColumnCheckIds.MOUNT_HIDE, "已安装 pathmask（${pathmaskDirs.first().substringAfterLast('/')}）")
            else -> failed(ColumnCheckIds.MOUNT_HIDE, "未装 mount 隐藏方案（Hybrid Mount / pathmask 都没有；多数银行不查这一层，可按需安装）")
        }
    }

    /** 7. Tricky Store 目标列表：target.txt 存在且至少有一行非空内容。 */
    private fun checkTargetList(shell: Shell): ColumnCheckResult {
        val out = shell.execCmd(
            "if [ -d /data/adb/tricky_store ]; then " +
                "if [ -f /data/adb/tricky_store/target.txt ]; then echo FILE; " +
                "grep -c '[^[:space:]]' /data/adb/tricky_store/target.txt; " +
                "else echo NOFILE; fi; else echo NODIR; fi"
        )
        return when (out.line(0)) {
            "NODIR" -> failed(ColumnCheckIds.TARGET_LIST, "未检测到 /data/adb/tricky_store（Tricky Store 未安装）")
            "NOFILE" -> failed(ColumnCheckIds.TARGET_LIST, "未检测到 /data/adb/tricky_store/target.txt（尚未配置目标应用）")
            "FILE" -> {
                // grep -c 只统计非空行：target.txt 里留几行空行不该算「配了目标」。
                val count = out.line(1)?.toIntOrNull() ?: 0
                if (count > 0) {
                    passed(ColumnCheckIds.TARGET_LIST, "target.txt 已配置 $count 个目标应用")
                } else {
                    failed(ColumnCheckIds.TARGET_LIST, "target.txt 为空，未配置任何目标应用")
                }
            }
            else -> failed(ColumnCheckIds.TARGET_LIST, "读取 target.txt 失败")
        }
    }

    /** keybox 可能落在这两个目录：TEESimulator 读 teesim，TrickyStore 读 tricky_store。 */
    private val keyboxPaths = listOf(
        "/data/adb/teesim/keybox.xml",
        "/data/adb/tricky_store/keybox.xml",
    )

    /** 超过这个天数没有任何更新，就在体检里提示换一份（keybox 会被 Google 吊销）。 */
    private val keyboxStaleDays = 7L

    /** 8. keybox：文件要够大，证书要没过期；放太久（>7 天）额外提示换一份。 */
    private fun checkKeybox(shell: Shell): ColumnCheckResult {
        val path = keyboxPaths.firstOrNull { shell.execCmd("test -f $it && echo y").text.trim() == "y" }
            ?: return failed(
                ColumnCheckIds.KEYBOX,
                "未检测到 keybox.xml（${keyboxPaths.joinToString(" / ")}）",
            )
        val probe = shell.execCmd("wc -c < $path; stat -c %Y $path 2>/dev/null || echo 0")
        val size = probe.line(0)?.trim()?.toLongOrNull() ?: 0L
        if (size <= 1000L) {
            return failed(ColumnCheckIds.KEYBOX, "keybox.xml 只有 $size 字节，内容不完整")
        }
        val modifiedSeconds = probe.line(1)?.trim()?.toLongOrNull() ?: 0L
        val ageDays = if (modifiedSeconds > 0L) {
            (System.currentTimeMillis() / 1000L - modifiedSeconds) / 86_400L
        } else {
            -1L
        }
        val stale = ageDays > keyboxStaleDays
        val ageNote = when {
            ageDays < 0L -> ""
            stale -> "，$ageDays 天没更新过，建议重新获取"
            else -> "，$ageDays 天前更新"
        }
        val xml = shell.execCmd("cat $path").text
        // 读不出到期时间不等于无效：keybox 的格式不止一种，缺 NotAfter 时只按大小判定，
        // 并在 detail 里说清楚「有效期没验」，不冒充成「已验证」。
        val expiry = parseKeyboxExpiry(xml)
            ?: return if (stale) {
                failed(ColumnCheckIds.KEYBOX, "文件存在（$size 字节）$ageNote，请重新获取后重试")
            } else {
                passed(ColumnCheckIds.KEYBOX, "文件存在（$size 字节）$ageNote，但未能解析证书到期时间，请自行确认有效期")
            }
        val today = LocalDate.now()
        return when {
            expiry.isBefore(today) -> failed(ColumnCheckIds.KEYBOX, "证书已在 $expiry 过期（$path$ageNote）")
            stale -> failed(ColumnCheckIds.KEYBOX, "证书到 $expiry，但$ageNote")
            else -> passed(ColumnCheckIds.KEYBOX, "证书有效，最早 $expiry 到期（$size 字节$ageNote）")
        }
    }

    /**
     * 从 keybox.xml 里取最早的一张证书到期日；取不到返回 null。
     *
     * keybox 里的 `NotAfter` 不是明文——它藏在 `<Certificate>` 的 PEM base64 里，所以先用
     * [CertificateFactory] 把 PEM 解出来读 notAfter（纯 JDK，不引依赖）；少见的 XML 变体如果直接
     * 写了 `notAfter="2027-05-01"` 这类文本，再退回正则兜底。
     */
    private fun parseKeyboxExpiry(xml: String): LocalDate? {
        val dates = PEM_CERTIFICATE_REGEX.findAll(xml).mapNotNull { match ->
            val base64 = match.value
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .filterNot { it.isWhitespace() }
            runCatching {
                val certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(Base64.getMimeDecoder().decode(base64)))
                (certificate as X509Certificate).notAfter
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }.getOrNull()
        }.toList()
        if (dates.isNotEmpty()) return dates.minOrNull()
        val text = TEXT_NOT_AFTER_REGEX.find(xml)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    /** 9. USB 调试：adb_enabled 为 0 才算通过。 */
    private fun checkAdb(shell: Shell): ColumnCheckResult {
        return when (val value = shell.execCmd("settings get global adb_enabled").text) {
            "0" -> passed(ColumnCheckIds.ADB, "adb_enabled = 0，USB 调试已关闭")
            "1" -> failed(ColumnCheckIds.ADB, "adb_enabled = 1，建议关闭 USB 调试")
            "null" -> failed(ColumnCheckIds.ADB, "adb_enabled 未设置（null），无法确认 USB 调试已关闭")
            "" -> failed(ColumnCheckIds.ADB, "读取 adb_enabled 失败（输出为空，可能没有 root 权限）")
            else -> failed(ColumnCheckIds.ADB, "adb_enabled 读取结果异常：$value")
        }
    }

    private fun passed(id: String, detail: String) = ColumnCheckResult(id, true, detail)

    private fun failed(id: String, detail: String) = ColumnCheckResult(id, false, detail)

    /** 一条 shell 命令的 stdout；只关心输出，退出码在这些「有输出就算数」的判据里没有用处。 */
    private class CommandOutput(val lines: List<String>) {
        /** 整段输出，多行用 \n 连接，首尾空白去掉。 */
        val text: String get() = lines.joinToString("\n").trim()

        /** 按行 trim 后的非空行。 */
        val nonBlankLines: List<String> get() = lines.map { it.trim() }.filter { it.isNotEmpty() }

        fun line(index: Int): String? = lines.getOrNull(index)?.trim()
    }

    private fun Shell.execCmd(vararg commands: String): CommandOutput {
        val collected = ArrayList<String>()
        newJob().add(*commands).to(collected, null).exec()
        return CommandOutput(collected)
    }

    private companion object {
        /** PEM 证书块；keybox 里通常有一到三张（叶证书 + 中间证书 + 根证书）。 */
        val PEM_CERTIFICATE_REGEX = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")

        /** 「notAfter = 2027-05-01」这类文本形式的兜底。 */
        val TEXT_NOT_AFTER_REGEX = Regex("(?i)notafter\\s*[=:]\\s*\"?([0-9]{4}-[0-9]{2}-[0-9]{2})")
    }
}
