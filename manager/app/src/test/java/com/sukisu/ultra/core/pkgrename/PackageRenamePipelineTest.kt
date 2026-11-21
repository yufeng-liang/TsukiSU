package com.sukisu.ultra.core.pkgrename

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 换包名全链路单测。
 *
 * 本机既没有真机也没有可用的模拟器镜像（`system-images` 缺失，AVD 起不来），
 * 所以这里是唯一的自动化验证手段：拿真实构建出来的 APK 跑一遍
 * 「改清单 → 重打包 → 重签」，再用 zip/apk 结构自检。
 * 更权威的复核交给桌面 `apksigner verify` 与 `aapt2 dump badging`（见 tools/pkgrename-spike）。
 *
 * 通过环境变量指定被测 APK：
 * ```
 * PKG_RENAME_APK=/h/sukisu/SukiSU-Ultra/manager/app/build/outputs/apk/debug/...apk \
 *   ./gradlew :app:testDebugUnitTest --tests '*PackageRenamePipelineTest*'
 * ```
 */
class PackageRenamePipelineTest {

    private val apkPath: String? =
        System.getProperty("pkg.rename.apk")?.takeIf { it.isNotBlank() }
            ?: System.getenv("PKG_RENAME_APK")?.takeIf { it.isNotBlank() }

    private fun keyMaterial(): ManagerKeyMaterial {
        val dir = File("src/main/assets/pkgrename")
        return ManagerKeyProvider.parse(
            File(dir, "manager-key.pk8").readBytes(),
            File(dir, "manager-cert.der").readBytes(),
        )
    }

    @Test
    fun 内置密钥可以解析且指纹正确() {
        val key = keyMaterial()
        assertEquals(745, key.certSize)
        assertEquals(
            "2a7ec59cb1cccf96ab2b8686d632356e5f4ac66e9188dbd5b0c24e7cc203e23f",
            key.certSha256Hex,
        )
        assertEquals("RSA", key.privateKey.algorithm)
    }

    @Test
    fun 包名校验与随机生成符合内核规则() {
        assertTrue(PackageNameUtils.isValid("com.a.b"))
        assertTrue(PackageNameUtils.isValid(PackageNameUtils.random()))
        assertFalse(PackageNameUtils.isValid("com"))
        assertFalse(PackageNameUtils.isValid("1com.a.b"))
        assertFalse(PackageNameUtils.isValid("com..b"))
        assertFalse(PackageNameUtils.isValid("com.a-b"))
        assertFalse(PackageNameUtils.isValid("com.a.b."))
        assertFalse(PackageNameUtils.isValid("a".repeat(PackageNameUtils.KERNEL_MAX_LENGTH + 1)))
        // 内核 package_valid() 的上限是 128，正好等于边界
        assertTrue(PackageNameUtils.isValid("com." + "a".repeat(PackageNameUtils.KERNEL_MAX_LENGTH - 4)))
    }

    @Test
    fun 真实APK改名后清单与签名都成立() {
        assumeTrue("未设置 PKG_RENAME_APK，跳过真实 APK 用例", apkPath != null && apkPath.isNotBlank())
        val apk = File(apkPath!!)
        assumeTrue("被测 APK 不存在：$apkPath", apk.isFile)

        val key = keyMaterial()
        val oldPackage = "com.yufeng.sukisu.ultra"
        val newPackage = PackageNameUtils.random()

        val srcManifest = ApkRepackager.readManifest(apk)
        assertTrue(
            "源 APK 清单里应有旧包名",
            ManifestPackageRewriter.countOccurrences(srcManifest, oldPackage) > 0,
        )

        val dir = File("build/tmp/pkgrename-test").apply { mkdirs() }
        val unsigned = File(dir, "unsigned.apk")
        val signed = File(dir, "signed-$newPackage.apk")

        val started = System.currentTimeMillis()
        ApkRepackager.repack(apk, unsigned) { manifest ->
            ManifestPackageRewriter.rewrite(manifest, oldPackage, newPackage)
        }
        ApkV2Signer.sign(unsigned, signed, key.privateKey, listOf(key.certificate))
        val elapsed = System.currentTimeMillis() - started

        assertTrue("产物应当存在", signed.isFile)
        assertTrue("产物不该是空文件", signed.length() > apk.length() / 2)

        val outManifest = ApkRepackager.readManifest(signed)
        assertTrue(
            "改名后清单应彻底换掉旧包名",
            ManifestPackageRewriter.verifyReplaced(outManifest, oldPackage, newPackage),
        )
        val strings = AxmlEditor.listStrings(outManifest)
        assertFalse("旧包名不应有任何残留", strings.any { it == oldPackage || it.startsWith("$oldPackage.") })

        // 签名块存在性：v2 块紧贴中央目录之前，尾巴是 magic
        val cdOffset = centralDirectoryOffset(signed)
        RandomAccessFile(signed, "r").use { raf ->
            val magic = ByteArray(16)
            raf.seek(cdOffset - 16L)
            raf.readFully(magic)
            assertEquals("APK Sig Block 42", String(magic, Charsets.US_ASCII))
        }

        println("换包名耗时 ${elapsed}ms，$oldPackage -> $newPackage，产物 ${signed.length()} 字节")
    }

    private fun centralDirectoryOffset(apk: File): Long {
        val fileLen = apk.length()
        val scanLen = minOf(fileLen, 0xFFFFL + 22L).toInt()
        val tail = ByteArray(scanLen)
        RandomAccessFile(apk, "r").use { raf ->
            raf.seek(fileLen - scanLen)
            raf.readFully(tail)
        }
        for (i in scanLen - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() &&
                tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
            ) {
                val off = (tail[i + 16].toLong() and 0xFF) or
                    ((tail[i + 17].toLong() and 0xFF) shl 8) or
                    ((tail[i + 18].toLong() and 0xFF) shl 16) or
                    ((tail[i + 19].toLong() and 0xFF) shl 24)
                return off
            }
        }
        throw IllegalStateException("找不到 zip 结尾记录")
    }
}
