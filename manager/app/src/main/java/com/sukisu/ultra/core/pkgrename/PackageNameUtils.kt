package com.sukisu.ultra.core.pkgrename

import java.security.SecureRandom

/** 包名不合法。 */
class InvalidPackageNameException(message: String) : Exception(message)

/**
 * 包名校验与随机生成。
 *
 * 校验规则刻意与内核 `dynamic_manager.c` 的 `package_valid()` 对齐：
 * 首字符 isalpha，其余只允许字母/数字/下划线/点，长度必须**小于** [KERNEL_MAX_LENGTH]。
 * 这里额外要求至少两段（含一个点），因为 Android 安装器不认单段包名。
 */
internal object PackageNameUtils {

    /** 与 `uapi/manager.h` 的 `KSU_MANAGER_PACKAGE_LENGTH` 一致（内核侧是 strnlen >= 128 就拒）。 */
    const val KERNEL_MAX_LENGTH = 128

    /**
     * 本工程源码的命名空间（R 类与组件类名所在包）。
     * 清单里以它开头的字符串是**类名**，换包名时一律不能动。
     */
    const val SOURCE_NAMESPACE = "com.sukisu.ultra"

    /** 出厂包名之外还要避开的名字，避免换回自己。按前缀判断（`com.android.foo` 也要避开）。 */
    private val RESERVED = listOf(
        "android",
        "androidx",
        "com.android",
        "com.google",
        "java",
        "kotlin",
        "com.sukisu",
    )

    private val RANDOM = SecureRandom()

    fun isValid(name: String): Boolean {
        // 内核侧是 strnlen(package, sizeof(package)) >= sizeof(package) 即拒，即最大只接受 127 个字符。
        if (name.isEmpty() || name.length >= KERNEL_MAX_LENGTH) return false
        // 内核用的是 strnlen，按字节算；只有 ASCII 时两者相等，仍显式判一次防回归。
        if (name.toByteArray(Charsets.UTF_8).size >= KERNEL_MAX_LENGTH) return false
        if (!name[0].isAsciiLetter()) return false
        for (c in name) {
            val ok = c.isAsciiLetter() || c in '0'..'9' || c == '_' || c == '.'
            if (!ok) return false
        }
        // 不允许连续点、末尾点、单段
        if (!name.contains('.')) return false
        if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) return false
        if (name.split('.').any { it.isEmpty() }) return false
        if (RESERVED.any { name == it || name.startsWith("$it.") }) return false
        return true
    }

    fun requireValid(name: String) {
        if (!isValid(name)) throw InvalidPackageNameException("包名不合法：$name")
    }

    /**
     * 生成随机包名 `com.<8位>.<8位>`，字母池刻意去掉容易看错的 l/i/o。
     * 与内核规则一致：全小写字母，因此必然合法。
     */
    fun random(prefix: String = "com"): String {
        val a = randomWord()
        val b = randomWord()
        return "$prefix.$a.$b"
    }

    private fun randomWord(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz"
        val sb = StringBuilder(8)
        repeat(8) { sb.append(alphabet[RANDOM.nextInt(alphabet.length)]) }
        return sb.toString()
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}

/**
 * 清单里的包名替换。
 *
 * 只改两类字符串：
 * 1. 与旧包名完全相等的那个（`package="..."` 属性）；
 * 2. 旧包名 + [SAFE_SUFFIXES] 白名单后缀（由 Gradle 把 `${applicationId}` 展开出来的
 *    taskAffinity、provider authorities、动态广播权限）。
 *
 * 其余以旧包名前缀开头的字符串是**组件类名**（`com.sukisu.ultra.MainActivity`），
 * 属于源码命名空间而不是安装包名，改了就会 ClassNotFoundException。
 */
internal object ManifestPackageRewriter {

    /** 允许跟着 applicationId 一起改名的后缀。新增 authorities/权限时要同步这里。 */
    private val SAFE_SUFFIXES = listOf(
        ".webui",
        ".fileprovider",
        ".androidx-startup",
        ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        ".pr",
    )

    /** 这条字符串是否属于「安装包名」而不是「类名」。 */
    fun isRewritable(s: String, oldPackage: String): Boolean =
        s == oldPackage || (s.startsWith("$oldPackage.") && SAFE_SUFFIXES.any { s.endsWith(it) })

    /**
     * @return 替换后的清单字节
     */
    fun rewrite(manifest: ByteArray, oldPackage: String, newPackage: String): ByteArray {
        if (oldPackage == newPackage) return manifest
        return AxmlEditor.rewriteStrings(manifest) { s ->
            if (!isRewritable(s, oldPackage)) s else newPackage + s.substring(oldPackage.length)
        }
    }

    /** 数一下清单里有多少个字符串会被替换（用于改名前的展示与自检）。 */
    fun countOccurrences(manifest: ByteArray, oldPackage: String): Int =
        AxmlEditor.listStrings(manifest).count { isRewritable(it, oldPackage) }

    /**
     * 校验替换结果：旧包名彻底消失、新包名存在，且没有未知后缀被静默留下。
     *
     * 出现未知的 `旧包名.xxx` 说明清单里还有本文件没覆盖的 applicationId 派生串，
     * 这时宁可失败也不能带着半个包名装出去。
     */
    fun verifyReplaced(manifest: ByteArray, oldPackage: String, newPackage: String): String? {
        val strings = AxmlEditor.listStrings(manifest)
        // 除了 applicationId 本身和 `旧包名.xxx`，还要拦 `旧包名:remote`（android:process）与
        // `旧包名/...`（组件、authority 拼接）：漏掉它们会出现「校验通过但清单里还有旧包名」。
        val leftover = strings.filter {
            it == oldPackage ||
                // 只有白名单后缀的派生串才算安装包名；其余「旧包名.Xxx」是组件类名（源码命名空间），
                // 一律按类名放过 —— 早先这里对任意前缀都判残留，包名等于源码命名空间时必然误报
                isRewritable(it, oldPackage) ||
                it.startsWith(oldPackage + ":") ||
                it.startsWith(oldPackage + "/")
        }
        if (leftover.isNotEmpty()) return "清单里仍有旧包名残留：${leftover.take(3)}"
        val present = strings.any { it == newPackage || it.startsWith("$newPackage.") }
        if (!present) return "清单里没有出现新包名 $newPackage"
        return null
    }
}
