package com.sukisu.ultra.core.pkgrename

import java.security.SecureRandom
import java.util.Locale

/** 包名不合法。 */
class InvalidPackageNameException(message: String) : Exception(message)

/**
 * 包名校验与随机生成。
 *
 * 校验规则刻意与内核 `dynamic_manager.c` 的 `package_valid()` 对齐：
 * 首字符 isalpha，其余只允许字母/数字/下划线/点，长度 1..[KERNEL_MAX]。
 * 这里额外要求至少两段（含一个点），因为 Android 安装器不认单段包名。
 */
internal object PackageNameUtils {

    /** 与 `uapi/manager.h` 的 `KSU_MANAGER_PACKAGE_LENGTH` 一致。 */
    const val KERNEL_MAX_LENGTH = 128

    /** 出厂包名之外还要避开的名字，避免换回自己。 */
    private val RESERVED = setOf(
        "android",
        "androidx",
        "com.android",
        "com.google",
        "java",
        "kotlin",
    )

    private val RANDOM = SecureRandom()

    fun isValid(name: String): Boolean {
        if (name.isEmpty() || name.length > KERNEL_MAX_LENGTH) return false
        if (!name[0].isAsciiLetter()) return false
        for (c in name) {
            val ok = c.isAsciiLetter() || c in '0'..'9' || c == '_' || c == '.'
            if (!ok) return false
        }
        // 不允许连续点、末尾点、单段
        if (!name.contains('.')) return false
        if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) return false
        if (name.split('.').any { it.isEmpty() }) return false
        if (RESERVED.contains(name)) return false
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

/** 清单里包名替换：把旧包名及其所有 `旧包名.xxx` 前缀一并换掉。 */
internal object ManifestPackageRewriter {

    /**
     * @return 替换后的清单字节
     */
    fun rewrite(manifest: ByteArray, oldPackage: String, newPackage: String): ByteArray {
        if (oldPackage == newPackage) return manifest
        return AxmlEditor.rewriteStrings(manifest) { s ->
            when {
                s == oldPackage -> newPackage
                s.startsWith("$oldPackage.") -> newPackage + s.substring(oldPackage.length)
                else -> s
            }
        }
    }

    /** 数一下清单里有多少个字符串会被替换（用于改名前的展示与自检）。 */
    fun countOccurrences(manifest: ByteArray, oldPackage: String): Int =
        AxmlEditor.listStrings(manifest).count { it == oldPackage || it.startsWith("$oldPackage.") }

    /** 校验替换后的清单里旧包名已彻底消失。 */
    fun verifyReplaced(manifest: ByteArray, oldPackage: String, newPackage: String): Boolean {
        val strings = AxmlEditor.listStrings(manifest)
        val leftover = strings.any { it == oldPackage || it.startsWith("$oldPackage.") }
        val present = strings.any { it == newPackage || it.startsWith("$newPackage.") }
        return !leftover && present
    }
}
