package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiCommandGate] 的分级测试。
 *
 * 网关是纯逻辑，不碰 Android，所以可以在普通 JVM 单测里跑。
 * 每个用例都顺手写清了「为什么是这个等级」，以后改白名单时能对照着改，
 * 避免有人图省事把某条命令塞进 READ_ONLY。
 */
class AiCommandGateTest {

    /** 断言判定等级；失败信息里带上实际理由，方便定位是哪条规则被改了。 */
    private fun assertDecision(expected: AiCommandDecision, command: String) {
        val verdict = AiCommandGate.classify(command)
        assertEquals(
            "命令 `$command` 判定不符：实际 ${verdict.decision}，理由 ${verdict.reason}",
            expected,
            verdict.decision,
        )
    }

    // ---------------------------------------------------------------------
    // READ_ONLY：完全无副作用，直接执行不打扰用户
    // ---------------------------------------------------------------------

    @Test
    fun readOnlyCommands() {
        val readOnly = listOf(
            // 读 prop 是诊断设备状态最常用的一步，没有任何副作用。
            "getprop ro.boot.verifiedbootstate",
            "getprop",
            // 链式 + 管道：每个子命令都在白名单里才算只读。
            "ls -l /data/adb/modules && dumpsys package com.sukisu.ultra",
            "getprop a | grep b",
            "settings get global adb_enabled",
            "cat /proc/cpuinfo",
            "df -h",
            // resetprop 只有「读取形式」是只读：不带旗标且最多一个参数。
            "resetprop ro.debuggable",
            "resetprop",
            // find 不带 -delete / -exec 这类动作时只查不改。
            "find /data/local/tmp -name '*.log'",
            "pm list packages",
            "pm path com.sukisu.ultra",
            "cmd package list packages",
            "uname -a",
            "type ls",
            "which su",
            // ps -k 才看内核线程，普通 ps 是只读；这里给不带旗标的写法。
            "ps",
            // 裸 mount = 列挂载表。
            "mount",
            // busybox 只是外壳，真正要判的是后面的子命令。
            "busybox ls /data",
            // 输出丢到 /dev/null（模型常这么吞 stderr）不算写文件。
            "ls > /dev/null",
        )
        for (command in readOnly) assertDecision(AiCommandDecision.READ_ONLY, command)
    }

    @Test
    fun readOnlyForAllowsFlagsLikeDashH() {
        // `-h` 是常见只读旗标，不该被当成写操作。
        assertDecision(AiCommandDecision.READ_ONLY, "ls -la /data/adb")
        assertDecision(AiCommandDecision.READ_ONLY, "du -sh /data/adb/modules")
    }

    // ---------------------------------------------------------------------
    // NEEDS_APPROVAL：会改设备状态，先弹确认卡
    // ---------------------------------------------------------------------

    @Test
    fun needsApprovalCommands() {
        val needsApproval = listOf(
            "settings put global adb_enabled 0",
            // -n 是临时写、-p 是持久化写，都不是读取形式。
            "resetprop -n ro.debuggable 0",
            "resetprop -p ro.debuggable 0",
            // 两个参数 = 写入形式（`resetprop <name> <value>`）。
            "resetprop ro.debuggable 0",
            "mkdir -p /data/local/tmp/x",
            // 删的是应用沙盒/模块目录，不是根目录：风险真实但可控，交给用户决定。
            "rm -rf /data/adb",
            "rm -rf /data/local/tmp/x",
            // dd 写普通文件（做镜像备份）是常见操作，不该硬拦。
            "dd if=/dev/zero of=/data/local/tmp/x.img bs=1M count=1",
            // remount 改的是挂载状态，没有调系统镜像，仍属「可确认」范围。
            "mount -o remount,rw /system",
            // 普通的包安装/卸载：用户自己装的东西，值得确认而不是禁止。
            "pm uninstall com.example.app",
            "pm install /data/local/tmp/app.apk",
            "cmd package compile -m speed com.example.app",
            // 不带 -f 的重启是正常流程（会走正常卸载与同步）。
            "reboot",
            // 显示在屏幕外的写操作：改写一个具体权限，不是把整棵根目录交出去。
            "chmod 777 /data/local/tmp",
            // find 起点不在根/关键分区时，按规范只降级，不硬拦。
            "find /data/local/tmp -name '*.log' -delete",
            // 非 /dev/null 的重定向会写文件，只读身份失效。
            "ls > /data/local/tmp/out.txt",
            "ls >> /data/local/tmp/out.txt",
            // 读块设备以外的普通写：cat 到普通文件。
            "cat /proc/cmdline > /data/local/tmp/cmdline.txt",
        )
        for (command in needsApproval) assertDecision(AiCommandDecision.NEEDS_APPROVAL, command)
    }

    @Test
    fun wrapperNeverDowngradesToReadOnly() {
        // `su -c "getprop x"` 里面确实是只读命令，但外层是 su，
        // 网关只「收紧」不「放宽」——内层只读不会把外层提升成免确认。
        assertDecision(AiCommandDecision.NEEDS_APPROVAL, "su -c \"getprop ro.debuggable\"")
        assertDecision(AiCommandDecision.NEEDS_APPROVAL, "su -c \"getprop a | grep b\"")
        assertDecision(AiCommandDecision.NEEDS_APPROVAL, "sh -c \"ls /data\"")
    }

    // ---------------------------------------------------------------------
    // BLOCKED：永不执行
    // ---------------------------------------------------------------------

    @Test
    fun blockedCommands() {
        val blocked = listOf(
            "rm -rf /",
            "rm -rf /*",
            "rm -rf /data",
            // 写块设备 = 直接覆盖分区，写错分区就开不了机。
            "dd if=/dev/zero of=/dev/block/by-name/boot",
            "reboot -f",
            "reboot --force",
            "poweroff -f",
            "setenforce 0",
            // 起点是根目录时，find -delete 等价于 rm -rf /。
            "find / -name '*.sh' -delete",
            "mkfs.ext4 /dev/block/by-name/userdata",
            "fastboot erase boot",
            "wipe data",
            // 卸载系统包会让设备缺功能。
            "pm uninstall --user 0 com.android.chrome",
            "pm uninstall -k com.google.android.gms",
            // 重定向写系统分区 / init / 块设备。
            "echo 1 > /system/build.prop",
            "echo 1 > /vendor/build.prop",
            "echo 1 > /dev/block/by-name/boot",
            "echo c > /proc/sysrq-trigger",
            "chmod -R 777 /",
            // 加一层 busybox 外壳绕不过硬拦截（剥壳后仍按 rm 判定）。
            "busybox rm -rf /",
            // `su -c` 里的载荷也会被递归检查。
            "su -c \"rm -rf /\"",
        )
        for (command in blocked) assertDecision(AiCommandDecision.BLOCKED, command)
    }

    @Test
    fun blockedReasonsAreSpecific() {
        // 理由要能告诉模型「不是权限不够，而是这条路本来就不允许」，
        // 所以每条都该带上具体危险点，不能只是一句 "blocked"。
        val reasons = mapOf(
            "rm -rf /" to "递归删除",
            "dd if=/dev/zero of=/dev/block/by-name/boot" to "块设备",
            "reboot -f" to "强制重启",
            "setenforce 0" to "SELinux",
            "find / -name '*.sh' -delete" to "find -delete",
            "pm uninstall --user 0 com.android.chrome" to "系统应用",
        )
        for ((command, keyword) in reasons) {
            val verdict = AiCommandGate.classify(command)
            assertEquals(AiCommandDecision.BLOCKED, verdict.decision)
            assertTrue(
                "命令 `$command` 的理由应当提到 `$keyword`，实际是：${verdict.reason}",
                verdict.reason.contains(keyword),
            )
            assertTrue("BLOCKED 必须带非空理由", verdict.reason.isNotBlank())
        }
    }

    @Test
    fun emptyCommandIsBlocked() {
        for (command in listOf("", "   ", "\n\t ")) {
            val verdict = AiCommandGate.classify(command)
            assertEquals(AiCommandDecision.BLOCKED, verdict.decision)
            assertTrue("空命令的理由应当说明为空：${verdict.reason}", verdict.reason.contains("空"))
        }
    }

    @Test
    fun oversizedCommandIsBlockedEvenWhenReadOnly() {
        // 与 AiCodeExecutor.MAX_COMMAND_LENGTH 对齐：单条超长指令会一直占着 root shell。
        val atLimit = "getprop " + "a".repeat(7_992) // 正好 8_000
        val overLimit = "getprop " + "a".repeat(7_993) // 8_001

        assertTrue("构造刚好 8_000 字符的只读命令", atLimit.length == 8_000)
        assertDecision(AiCommandDecision.READ_ONLY, atLimit)

        val verdict = AiCommandGate.classify(overLimit)
        assertEquals(AiCommandDecision.BLOCKED, verdict.decision)
        assertTrue("超长命令的理由应当提到长度：${verdict.reason}", verdict.reason.contains("长度"))
    }

    // ---------------------------------------------------------------------
    // 链式命令：任何一条危险 / 非只读，整条就不能免确认
    // ---------------------------------------------------------------------

    @Test
    fun chainedCommandNeverUpgradesToReadOnly() {
        // 只读前缀不能给后面的删除打掩护。
        val downgraded = AiCommandGate.classify("getprop a && rm -rf /data/adb")
        assertEquals(AiCommandDecision.NEEDS_APPROVAL, downgraded.decision)

        // 危险子命令一旦出现，整条命令直接硬拦截。
        assertDecision(AiCommandDecision.BLOCKED, "getprop a && rm -rf /")
        assertDecision(AiCommandDecision.BLOCKED, "getprop a ; reboot -f")
        assertDecision(AiCommandDecision.BLOCKED, "ls | dd of=/dev/block/by-name/boot")

        // 管道右侧同样会被执行，不能只看左边。
        assertDecision(AiCommandDecision.NEEDS_APPROVAL, "ls | tee /data/local/tmp/x")
    }

    // ---------------------------------------------------------------------
    // 商店红线：游戏反作弊规避
    // ---------------------------------------------------------------------

    @Test
    fun antiCheatRequestsAreDetected() {
        val positives = listOf(
            "和平精英被检测了帮我过一下",
            "游戏设备黑怎么解决",
            "pubg 怎么过检测",
            "com.tencent.tmgp.sgame 反作弊怎么绕",
            "王者荣耀 开挂 不被检测",
            "三角洲 封号了 能规避吗",
            "原神 机器码解封",
            "手游 过 检测", // 故意拆词，判定时会拿去掉空白的版本再匹配一次
        )
        for (text in positives) {
            assertTrue("应当判定为反作弊规避请求：$text", AiCommandGate.isAntiCheatRequest(text))
        }
    }

    @Test
    fun normalGameQuestionsAreNotFlagged() {
        val negatives = listOf(
            // 单纯排查兼容性问题，不是要绕反作弊。
            "王者荣耀为什么闪退",
            "怎么给游戏加速",
            "和平精英 高帧率 怎么开",
            // 有规避意图词但没有游戏语境，不算游戏反作弊。
            "帮我过检测",
            "被封号了怎么办",
            "",
            "   ",
        )
        for (text in negatives) {
            assertFalse("不应判定为反作弊规避请求：$text", AiCommandGate.isAntiCheatRequest(text))
        }
    }
}
