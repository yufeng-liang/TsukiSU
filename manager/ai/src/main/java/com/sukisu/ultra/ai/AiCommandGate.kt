package com.sukisu.ultra.ai

import java.util.Locale

/** 网关对一条待执行命令的判定。 */
enum class AiCommandDecision {
    /** 安全的只读探测：直接执行，不用打扰用户。 */
    READ_ONLY,

    /** 有副作用：先给用户看确认卡片。 */
    NEEDS_APPROVAL,

    /** 硬拦截：永不执行，把理由回传给模型让它换一种做法。 */
    BLOCKED,
}

/** 一条命令的判定结果与理由。 */
data class AiCommandVerdict(
    val decision: AiCommandDecision,
    /** 简短的人类可读理由，既给用户看，也回传给模型。 */
    val reason: String = "",
)

/**
 * AI 命令网关：在模型提议执行 shell 命令之后、真正落地之前做分级与拦截。
 *
 * 约定：
 * - 纯逻辑，不碰 Android / Context，可以直接在 JVM 单测里跑。
 * - 判定只「收紧」不「放宽」：被包装过的命令（`sh -c`、`su -c`、`eval`）
 *   只会因为内层命中危险规则而升级成 [AiCommandDecision.BLOCKED]，
 *   不会因为内层只读就降级成 [AiCommandDecision.READ_ONLY]。
 * - 拿不准就走 [AiCommandDecision.NEEDS_APPROVAL]，绝不猜「应该没事」。
 */
object AiCommandGate {

    /**
     * 长度上限，和 `AiCodeExecutor.MAX_COMMAND_LENGTH` 保持一致。
     * run_shell 工具自己没有别的长度保护，所以这里必须拦住：
     * 单条超长指令会一直占着 root shell。
     */
    private const val MAX_COMMAND_LENGTH = 8_000

    /** `sh -c` / `su -c` / `eval` 最多再往里看三层，防止构造出来的无限嵌套。 */
    private const val MAX_WRAPPER_DEPTH = 3

    /** 把一条命令行拆成若干子命令后分别判定；`|` 也算拆，因为管道右侧同样会执行。 */
    fun classify(command: String): AiCommandVerdict {
        if (command.isBlank()) return blocked("命令为空")

        val segments = splitSegments(tokenize(command))
        if (segments.isEmpty()) return blocked("命令为空")

        // 1) 危险模式优先：命中就直接硬拦截，理由要指向具体风险，
        //    这样模型才知道「不是权限不够，而是这条路本来就不允许」。
        for (segment in segments) {
            dangerReason(segment, depth = 0)?.let { return blocked(it) }
        }

        // 2) 动态展开：`$()`、`${}`、反引号、`<()` / `>()` 是执行时才展开的，
        //    静态分词只能把它们当成字面量，白名单因此不可信。先把展开内容本身
        //    过一遍危险规则（`cat "$(rm -rf /data)"` 要硬拦），剩下的统一降级确认。
        val expansion = scanDynamicExpansion(command)
        for (payload in expansion.payloads) {
            for (inner in splitSegments(tokenize(payload))) {
                dangerReason(inner, depth = 0)?.let { return blocked(it) }
            }
        }

        // 3) 长度上限：即使内容只读也不放行（见 MAX_COMMAND_LENGTH 注释）。
        if (command.length > MAX_COMMAND_LENGTH) {
            return blocked(
                "命令长度 ${command.length} 超过 $MAX_COMMAND_LENGTH 字符上限，请拆成更小的步骤"
            )
        }

        // 4) 只读白名单：必须每个子命令都只读，只要有一条不是就降级。
        //    出现动态展开时白名单整体失效：展开以后真正执行的命令没有经过分级。
        if (!expansion.found && segments.all { segmentIsReadOnly(it) }) {
            return AiCommandVerdict(AiCommandDecision.READ_ONLY, "只读探测")
        }

        // 5) 其余一律需要用户确认。
        if (expansion.found) {
            return AiCommandVerdict(
                AiCommandDecision.NEEDS_APPROVAL,
                "命令包含 shell 动态展开（${'$'}()、${'$'}{}、反引号或进程替换），需要确认",
            )
        }
        return AiCommandVerdict(AiCommandDecision.NEEDS_APPROVAL, "会修改设备状态，需要确认")
    }

    // ---------------------------------------------------------------------
    // 危险模式（BLOCKED）
    // ---------------------------------------------------------------------

    /** 扫描一条子命令，命中危险规则就返回具体理由，否则返回 null。 */
    private fun dangerReason(tokens: List<String>, depth: Int): String? {
        val command = stripMultiplexers(tokens)
        if (command.isEmpty()) return null
        val name = commandName(command[0])
        val args = command.drop(1)

        // `sh -c "…"` / `su -c "…"` / `eval "…"` 里其实是另一条完整命令，
        // 递归看一眼再判定；否则 `su -c "rm -rf /"` 会漏过硬拦截。
        if (depth < MAX_WRAPPER_DEPTH) {
            val payload = wrapperPayload(name, args)
            if (payload != null) {
                for (inner in splitSegments(tokenize(payload))) {
                    dangerReason(inner, depth + 1)?.let { return it }
                }
                // 外层引号有时只是把整段文字交给内层 shell 再解析
                // （`su -c 'cat "$(rm -rf /)"'`）：载荷里的动态展开同样要递归检查。
                for (expanded in scanDynamicExpansion(payload).payloads) {
                    for (inner in splitSegments(tokenize(expanded))) {
                        dangerReason(inner, depth + 1)?.let { return it }
                    }
                }
            }
        }

        // 重定向的目标单独看：`> /boot/x` 和 `> /dev/block/…` 都是刻盘级操作。
        redirectDanger(command)?.let { return it }

        return commandDanger(name, args)
    }

    /** 按命令名匹配的危险规则。每条都写清楚为什么不能放行。 */
    private fun commandDanger(name: String, args: List<String>): String? {
        // ---- 分区 / 磁盘级：执行完就是丢数据或者变砖 ----

        // mkfs、format、wipe 会重建文件系统，没有「撤销」这个选项。
        if (name == "mkfs" || name.startsWith("mkfs.")) return "禁止格式化分区（mkfs）"
        if (name == "format") return "禁止格式化分区（format）"
        if (name == "wipe") return "禁止擦除数据（wipe）"

        // fastboot erase 直接擦分区，刷机流程里最容易把设备刷成砖的一步。
        if (name == "fastboot" && args.contains("erase")) return "禁止 fastboot erase 擦除分区"

        // dd 写到块设备 = 直接覆盖分区内容（写错就开不了机）。
        // 只拦块设备：`of=/dev/null`、`of=/dev/stdout` 这类是正常的丢弃输出。
        if (name == "dd") {
            val target = args.firstOrNull { it.startsWith("of=") }?.removePrefix("of=")
            if (target != null && isBlockDeviceWrite(target)) {
                return "禁止 dd 覆写块设备（$target），会破坏 boot 或分区"
            }
        }

        // ---- 删除 / 权限 / 安全开关 ----

        // 递归删除根目录或关键分区。这里只做「整段相等」判定，
        // 所以 `rm -rf /data/local/tmp/x` 不会被硬拦（它走「需要确认」）。
        if (name == "rm") {
            val recursive = args.contains("--recursive") || args.hasShortFlag('r')
            if (recursive) {
                val target = args.filterNot { it.startsWith("-") }.firstOrNull { isCriticalPath(it) }
                if (target != null) return "禁止递归删除根目录或关键分区（$target）"
            }
        }

        // find 的 -delete / -exec 本身就是删除或执行，起点在根目录时等同 rm -rf。
        if (name == "find") {
            val start = args.firstOrNull { !it.startsWith("-") } ?: "."
            val criticalStart = isCriticalPath(start)
            if (criticalStart && args.contains("-delete")) {
                return "禁止从根目录或关键分区递归删除（find -delete）"
            }
            val execIndex = args.indexOfFirst { it in FIND_EXECUTORS }
            if (criticalStart && execIndex >= 0) {
                val executed = args.getOrNull(execIndex + 1)?.let(::commandName)
                if (executed != null && isDestructiveCommand(executed)) {
                    return "禁止在根目录上用 find -exec 执行破坏性命令（$executed）"
                }
            }
        }

        // `chmod -R 777 /` 等于把所有系统文件交给任何应用改写。
        if (name == "chmod") {
            val operands = args.filterNot { it.startsWith("-") }
            val mode = operands.firstOrNull()
            val target = operands.getOrNull(1)
            if (mode != null && mode in WORLD_WRITABLE_MODES && target != null && isCriticalPath(target)) {
                return "禁止把根目录改成 $mode（任何应用都能改写系统文件）"
            }
        }

        // 临时关 SELinux 会让整机强制访问控制失效，属于一眼就能看出的越界。
        if (name == "setenforce") {
            val value = args.firstOrNull()?.lowercase(Locale.ROOT)
            if (value == "0" || value == "permissive") {
                return "禁止关闭 SELinux（setenforce 0 会让全局强制访问控制失效）"
            }
        }

        // 卸载系统包会让设备功能缺失，pm 和 cmd 两条路径都拦。
        if ((name == "pm" || name == "cmd") && args.contains("uninstall")) {
            val pkg = args.firstOrNull { it.startsWith("com.android") || it.startsWith("com.google") }
            if (pkg != null) return "禁止卸载系统应用（$pkg），会导致设备功能缺失"
        }

        // ---- 强制重启 / 断电 ----
        // -f 跳过正常卸载与同步，有丢数据、坏文件系统的风险。
        if (name == "reboot" || name == "poweroff" || name == "halt") {
            if (args.contains("--force") || args.hasShortFlag('f')) {
                return "禁止强制重启或断电（$name -f），可能丢数据并损坏文件系统"
            }
        }

        return null
    }

    /** 检查 `>` / `>>` 的目标；写块设备或写系统分区都直接拦。 */
    private fun redirectDanger(tokens: List<String>): String? {
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token != ">" && token != ">>") continue
            val target = tokens.getOrNull(index + 1) ?: continue
            if (isBlockDeviceWrite(target)) {
                return "禁止把输出重定向写入块设备（$target），会覆盖分区"
            }
            if (isSystemRootWrite(target)) {
                return "禁止写入系统分区或 /init（$target），需要重新打包系统镜像"
            }
            // 目标由命令替换生成时（`> "$(echo /system/x)"`）最终路径静态看不出来，
            // 但展开内容里出现的路径同样要过黑名单，不然换个引号写法就绕过。
            for (payload in scanDynamicExpansion(target).payloads) {
                val dangerous = splitSegments(tokenize(payload))
                    .flatten()
                    .any { isBlockDeviceWrite(it) || isSystemRootWrite(it) }
                if (dangerous) {
                    return "禁止把输出重定向写入系统分区或块设备（目标由命令替换生成：$target）"
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------------
    // 只读白名单（READ_ONLY）
    // ---------------------------------------------------------------------

    /**
     * 判断一条子命令是否只读。白名单按前缀匹配（`commandName` 已经去掉 `/system/bin/` 这类路径）：
     *
     * - `getprop`：读任何属性，无副作用。
     * - `resetprop`：只有「不带旗标且最多一个参数」的读取形式算只读；
     *   `-n`（临时写入）、`-p`（持久化）、`名字 值`（写入）都不算。
     * - `ls` / `cat` / `head` / `tail` / `wc` / `grep` / `stat` / `du` / `df`：纯读取。
     * - `find`：不带写操作动作（`-delete`、`-exec`、`-ok`、`-fprint` 等）时只读。
     * - `dumpsys` / `pm list` / `pm path` / `cmd package list` / `settings get`：查询系统状态。
     * - `getenforce` / `id` / `uname` / `whoami` / `pidof`：纯查询。
     * - `ps`：不带 `-k`（看内核线程）时只读。
     * - `mount`：不带参数或只带 `-l` 时是列挂载表；带 `-o remount` 之类就降级。
     * - `busybox` / `toybox`：剥掉外壳后再看真正的子命令。
     * - `sha256sum` / `md5sum` / `which` / `type`：纯读取或纯查询。
     *
     * 其余一律不算只读。额外的收紧：
     * - 任何重定向（除 `> /dev/null`）都让只读失效，因为那会写文件。
     * - `<` 是输入重定向，只是喂 stdin，允许保留只读身份。
     */
    private fun segmentIsReadOnly(tokens: List<String>): Boolean {
        val command = stripMultiplexers(tokens)
        if (command.isEmpty()) return false
        val name = commandName(command[0])
        val args = command.drop(1)

        // 输出重定向到 /dev/null 是模型常用写法（吞掉 stderr），无害；
        // 写到别处就是副作用，不能算只读。
        val redirects = command.indices.filter { command[it] == ">" || command[it] == ">>" }
        if (redirects.isNotEmpty()) {
            val targets = redirects.map { command.getOrNull(it + 1) }
            if (targets.any { it != "/dev/null" }) return false
        }

        return when (name) {
            "getprop" -> true

            // 读取形式：`resetprop`（列出全部）或 `resetprop <name>`。
            "resetprop" -> args.size <= 1 && args.none { it.startsWith("-") }

            "ls", "cat", "head", "tail", "wc", "grep", "stat", "du", "df",
            "getenforce", "id", "uname", "whoami", "pidof",
            "sha256sum", "md5sum", "which", "type",
            -> true

            // dumpsys 只有「服务名 + 服务自己的筛选词」是查询；参数里出现路径，
            // 或像是指定输出/文件的旗标时，某些服务会把状态写盘，降级成人工确认。
            "dumpsys" -> args.none { isPathArgument(it) || it in DUMPSYS_OUTPUT_FLAGS }

            // find 只要带写操作动作就不是只读（起点在根目录时已在上面的危险规则里拦掉）。
            "find" -> args.none { it in FIND_WRITERS }

            "pm" -> args.isNotEmpty() && args[0] in QUERY_PM_ACTIONS

            "cmd" -> args.size >= 2 && args[0] == "package" && args[1] == "list"

            "settings" -> args.firstOrNull() == "get"

            // ps -k 看内核线程，不属于普通只读探测。
            "ps" -> args.none { it.startsWith("-") && !it.startsWith("--") && it.drop(1).contains('k') }

            // mount 裸命令 = 列挂载表；带任何参数都可能是 remount，交给人工确认。
            "mount" -> args.isEmpty() || (args.size == 1 && args[0] == "-l")

            else -> false
        }
    }

    // ---------------------------------------------------------------------
    // 商店红线：游戏反作弊规避
    // ---------------------------------------------------------------------

    /**
     * `isAntiCheatRequest` 的判定：必须同时命中「游戏标识」与「规避意图」才算 true。
     * 只说「王者荣耀为什么闪退」不算——那是在排查兼容性，不是要绕反作弊。
     *
     * 这是商店红线：不提供任何游戏反作弊规避能力，模型给的理由再充分也不改变判定。
     */
    fun isAntiCheatRequest(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        // 中文里有人会把词拆开写（「过 检测」），所以再拿去掉空白的版本匹配一次。
        val compact = lower.filterNot { it.isWhitespace() }

        val game = GAME_HINTS.any { lower.contains(it) } || mentionsCrossfire(lower)
        if (!game) return false

        return EVASION_HINTS.any { lower.contains(it) || compact.contains(it) }
    }

    // ---------------------------------------------------------------------
    // 词表与工具函数
    // ---------------------------------------------------------------------

    /** 游戏包名 / 名称。`游戏`、`game` 这类泛称也算，因为反作弊话题几乎都绕不开它们。 */
    private val GAME_HINTS = listOf(
        "com.tencent.tmgp", "com.proximabeta.mf.uamo", "com.garena",
        "com.proxima.dfm", "com.tencent.mf.uam",
        "pubg", "codm",
        "和平精英", "王者荣耀", "三角洲", "暗区突围", "使命召唤", "原神", "崩坏",
        "游戏", "手游", "game",
    )

    /** 规避意图词：出现这些词说明诉求是「别被检测到」，而不是「游戏跑不起来」。 */
    private val EVASION_HINTS = listOf(
        "反作弊", "过检测", "绕过检测", "规避检测", "逃避检测", "不被检测",
        "隐藏游戏检测", "被检测", "过验证", "机器码",
        "规避", "封号", "被封", "设备黑",
    )

    /** `cf` 只有两个字母，容易撞上 `.cf` 域名、cfg 文件名，必须配合游戏语境才算。 */
    private val CROSSFIRE = Regex("(?<![a-z0-9_])cf(?![a-z0-9_])")

    /** 配合 `cf` 使用的游戏语境词。 */
    private val GAME_CONTEXT = listOf("游戏", "手游", "game", "外挂", "开挂")

    private fun mentionsCrossfire(text: String): Boolean =
        CROSSFIRE.containsMatchIn(text) && GAME_CONTEXT.any { text.contains(it) }

    /** 只读系统分区：这些位置下面的任何东西都不该被删除或改写。 */
    private val READ_ONLY_PARTITIONS = listOf(
        "/system", "/system_ext", "/vendor", "/odm", "/product", "/boot",
        "/dev", "/proc", "/sys", "/metadata", "/persist", "/efs", "/firmware",
    )

    /** 必须整段相等才算命中的根位置（子目录另算，避免误伤 /data/local/tmp）。 */
    private val ROOT_PATHS = setOf("/", "/data", "/mnt", "/sbin", "/bin", "/init", "/init.rc")

    /**
     * 是否是「根目录 / 关键分区」。
     * 支持以通配收尾的写法（`rm -rf /data/` 与 `rm -rf /data` 等价），
     * 但 `/data/local/tmp/x` 这种具体子路径刻意不算，它只需要用户确认。
     */
    private fun isCriticalPath(raw: String): Boolean {
        var path = raw.trim()
        if (path.isEmpty()) return false
        if (path.endsWith("/*")) path = path.dropLast(2)
        path = path.trimEnd('/')
        if (path.isEmpty()) return true
        if (path in ROOT_PATHS) return true
        return READ_ONLY_PARTITIONS.any { path == it || path.startsWith("$it/") }
    }

    /** 写块设备（写错就覆盖分区）；`/dev/null`、`/dev/stdout` 这类丢弃输出的目标不算。 */
    private val SAFE_DEVICE_TARGETS = setOf(
        "/dev/null", "/dev/zero", "/dev/full", "/dev/tty",
        "/dev/stdin", "/dev/stdout", "/dev/stderr",
    )

    private fun isBlockDeviceWrite(raw: String): Boolean {
        val path = raw.trim()
        if (path in SAFE_DEVICE_TARGETS) return false
        if (path.startsWith("/dev/pts/") || path.startsWith("/dev/fd/")) return false
        if (path.startsWith("/dev/block")) return true
        if (path.startsWith("/dev/sd") || path.startsWith("/dev/mmcblk")) return true
        if (path.startsWith("/dev/nvme") || path.startsWith("/dev/disk/")) return true
        if (path.startsWith("/proc/sysrq-trigger")) return true
        return false
    }

    /** 直接写系统分区或 /init：这两处只能整包刷新，不能临时改。 */
    private fun isSystemRootWrite(raw: String): Boolean {
        val path = raw.trim()
        return path == "/system" || path.startsWith("/system/") ||
            path == "/vendor" || path.startsWith("/vendor/") ||
            path == "/boot" || path.startsWith("/boot/") ||
            path == "/init" || path.startsWith("/init.")
    }

    /** find 里出现这些动作就不再是「只看不改」。 */
    private val FIND_WRITERS = setOf(
        "-delete", "-exec", "-execdir", "-ok", "-okdir",
        "-fprint", "-fprint0", "-fprintf", "-fls",
    )

    /** find -exec 里会真的改动设备的命令。 */
    private val FIND_EXECUTORS = setOf("-exec", "-execdir", "-ok", "-okdir")

    private fun isDestructiveCommand(name: String): Boolean =
        name in setOf("rm", "dd", "mv", "chmod", "chown", "truncate", "shred", "wipe", "format") ||
            name.startsWith("mkfs")

    /** 等同于「任何人都能读写」的权限模式。 */
    private val WORLD_WRITABLE_MODES = setOf("777", "0777", "7777", "0o777", "a+rwx", "ugo+rwx")

    /** `pm` 里不会改状态的子命令。 */
    private val QUERY_PM_ACTIONS = setOf("list", "path")

    /** 只是换一层壳的多路复用命令，真正的命令在它后面。 */
    private val MULTIPLEXERS = setOf("busybox", "toybox")

    /** 会把后面的字符串当命令执行的入口。 */
    private val SHELLS = setOf("sh", "bash", "ash", "mksh", "dash", "zsh")

    /**
     * 取包装命令里的载荷：`sh -c …`、`su … -c …`、`eval …`。
     * 返回 null 表示这条命令不是包装器。
     */
    private fun wrapperPayload(name: String, args: List<String>): String? {
        if (name in SHELLS || name == "su") {
            val index = args.indexOfFirst { it == "-c" || it == "--command" }
            return if (index >= 0) args.getOrNull(index + 1) else null
        }
        if (name == "eval") return args.joinToString(" ").ifBlank { null }
        return null
    }

    /** 去掉 `busybox` / `toybox` 前缀；旗标本身不动，因为 `busybox --install` 也是写操作。 */
    private fun stripMultiplexers(tokens: List<String>): List<String> {
        var result = tokens
        while (result.size > 1 && commandName(result[0]) in MULTIPLEXERS) {
            result = result.drop(1)
        }
        return result
    }

    /** `/system/bin/rm` → `rm`。 */
    private fun commandName(token: String): String = token.substringAfterLast('/')

    /** `-f`、`-Rf` 这类单横线旗标；`--force` 不算。 */
    private fun isShortFlag(token: String): Boolean =
        token.length > 1 && token.startsWith("-") && !token.startsWith("--")

    private fun List<String>.hasShortFlag(letter: Char): Boolean =
        any { isShortFlag(it) && it.drop(1).contains(letter, ignoreCase = true) }

    private fun blocked(reason: String) = AiCommandVerdict(AiCommandDecision.BLOCKED, reason)

    // ---------------------------------------------------------------------
    // 动态展开扫描（命令替换 / 变量展开 / 反引号 / 进程替换）
    // ---------------------------------------------------------------------

    /**
     * [scanDynamicExpansion] 的结果。
     *
     * [found] 为 true 说明命令里有 shell 执行时才展开的结构；[payloads] 是这些结构
     * 内部的原始文本，拿去再过一遍危险规则（`cat "$(rm -rf /)"` 要硬拦而不只是确认）。
     */
    private data class ExpansionScan(val found: Boolean, val payloads: List<String>)

    /**
     * 扫描 shell 动态展开：`$( )`、`${ }`、反引号、`<(`、`>(`。
     *
     * 为什么必须单独扫一遍原文：[tokenize] 会把引号里的内容原样聚成一个字面量 token，
     * `cat "$(rm -rf /data)"` 于是看起来只是「cat 带一个普通参数」而判成只读，
     * 可 shell 真正执行时会先跑掉 `rm -rf /data` —— 等于任意命令免确认拿 root。
     * 只读判定前必须先过这道扫描。
     *
     * 引号规则：单引号内 shell 不展开，整段跳过；双引号内 `$()`、`${}`、反引号
     * 照样展开，必须算。[payloads] 的截取按括号嵌套尽力而为（引号嵌套时可能截短），
     * 但判定只依赖 [found]，少截一段最多是少一次硬拦，不会少一次确认。
     */
    private fun scanDynamicExpansion(text: String): ExpansionScan {
        val payloads = ArrayList<String>()
        var found = false
        var index = 0
        var inDoubleQuote = false

        while (index < text.length) {
            val char = text[index]

            // 单引号内：shell 原样保留，直接跳到闭合的单引号。
            if (!inDoubleQuote && char == '\'') {
                val end = text.indexOf('\'', index + 1)
                index = if (end < 0) text.length else end + 1
                continue
            }

            // 双引号：进出状态要记下来，里面的 `$()` / `${}` / 反引号仍然要检测。
            if (char == '"') {
                inDoubleQuote = !inDoubleQuote
                index++
                continue
            }

            val next = if (index + 1 < text.length) text[index + 1] else ' '
            when {
                char == '$' && next == '(' -> {
                    found = true
                    val end = matchingBracket(text, index + 2, '(', ')')
                    payloads.add(text.substring(index + 2, end))
                    index = end + 1
                }

                char == '$' && next == '{' -> {
                    found = true
                    val end = matchingBracket(text, index + 2, '{', '}')
                    payloads.add(text.substring(index + 2, end))
                    index = end + 1
                }

                char == '`' -> {
                    found = true
                    val end = text.indexOf('`', index + 1)
                    if (end < 0) {
                        // 反引号没闭合：shell 要么报错要么把后面整段吞进去，一样要人看。
                        payloads.add(text.substring(index + 1))
                        index = text.length
                    } else {
                        payloads.add(text.substring(index + 1, end))
                        index = end + 1
                    }
                }

                // 进程替换：`<(cmd)` / `>(cmd)` 会起一个真实的进程并给它一个 /dev/fd 路径。
                !inDoubleQuote && (char == '<' || char == '>') && next == '(' -> {
                    found = true
                    val end = matchingBracket(text, index + 2, '(', ')')
                    payloads.add(text.substring(index + 2, end))
                    index = end + 1
                }

                else -> index++
            }
        }
        return ExpansionScan(found, payloads)
    }

    /** 从 [start] 起找与 [open]/[close] 配对的括号位置（计入嵌套），找不到就返回文本末尾。 */
    private fun matchingBracket(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 1
        var index = start
        while (index < text.length) {
            val char = text[index]
            when {
                char == open -> depth++
                char == close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return text.length
    }

    /**
     * 参数是否看起来指到某个文件：绝对路径、`./x`、`../x`、`~` 开头，
     * 或带路径取值的旗标（`--file=/data/x` 的旗标值部分）。
     */
    private fun isPathArgument(token: String): Boolean {
        val value = token.substringAfter('=', token)
        return value.startsWith("/") || value.startsWith("./") ||
            value.startsWith("../") || value.startsWith("~")
    }

    /**
     * dumpsys 各服务的实现互不相同，出现这些「指定输出/文件」意味的旗标时不能假定它只是
     * 往 stdout 打一份文本；连同 [isPathArgument] 一起把这类参数降级成人工确认。
     */
    private val DUMPSYS_OUTPUT_FLAGS = setOf(
        "-o", "--output", "--out", "-f", "--file", "--save",
    )

    /**
     * 把命令行切成 token。和 shell 一样处理引号，并把操作符单独拆成 token，
     * 这样 `x>/system/f` 和 `x > /system/f` 得到同样的结果。
     */
    private fun tokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var index = 0

        fun flush() {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.setLength(0)
            }
        }

        while (index < text.length) {
            val char = text[index]
            when {
                quote != null -> {
                    if (char == quote) quote = null else current.append(char)
                    index++
                }

                char == '\'' || char == '"' -> {
                    quote = char
                    index++
                }

                // 反斜杠转义：把下一个字符原样吃进来（引号内的反斜杠按字面处理）。
                char == '\\' && index + 1 < text.length -> {
                    current.append(text[index + 1])
                    index += 2
                }

                char == '>' || char == '<' || char == '&' || char == '|' ||
                    char == ';' || char == '(' || char == ')' -> {
                    flush()
                    val pair = if (index + 1 < text.length) text.substring(index, index + 2) else ""
                    if (pair == ">>" || pair == "<<" || pair == "&&" || pair == "||") {
                        tokens.add(pair)
                        index += 2
                    } else {
                        tokens.add(char.toString())
                        index++
                    }
                }

                char == '\n' -> {
                    // 换行在 shell 里等价于 `;`。
                    flush()
                    tokens.add(";")
                    index++
                }

                char.isWhitespace() -> {
                    flush()
                    index++
                }

                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        flush()
        return tokens
    }

    /** 按 `&&`、`||`、`;`、`|`、`&`、括号切子命令；引号内的内容已经在 tokenize 里聚成单个 token。 */
    private fun splitSegments(tokens: List<String>): List<List<String>> {
        val segments = ArrayList<List<String>>()
        var current = ArrayList<String>()
        for (token in tokens) {
            if (token in SEPARATORS) {
                if (current.isNotEmpty()) {
                    segments.add(current)
                    current = ArrayList()
                }
            } else {
                current.add(token)
            }
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments
    }

    /** 这些 token 表示「换一条命令」，判定时要分开算。 */
    private val SEPARATORS = setOf("&&", "||", ";", "|", "&", "(", ")")
}
