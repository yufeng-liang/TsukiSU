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

    /** 现场生成一对 RSA 密钥与自签证书，等价于设备首次换名时做的事。 */
    private fun generatedKey(): ManagerKeyMaterial {
        val pair = java.security.KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
        val der = X509SelfSigned.build(pair, "SukiSU Test")
        return ManagerKeyProvider.parse(pair.private.encoded, der)
    }

    @Test
    fun 现场生成的密钥与自签证书自洽() {
        val pair = java.security.KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
        val der = X509SelfSigned.build(pair, "SukiSU Test")
        val key = ManagerKeyProvider.parse(pair.private.encoded, der)
        assertEquals("RSA", key.privateKey.algorithm)
        assertEquals(2048, (key.privateKey as java.security.interfaces.RSAPrivateKey).modulus.bitLength())
        // verify 返回 void，抛 CertificateException 即算失败，JUnit 会直接判红
        key.certificate.verify(pair.public)
        assertEquals(64, key.certSha256Hex.length)
        assertTrue("证书 DER 应当是合法 X.509", key.certSize > 600)
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
        // 内核是 strnlen >= 128 即拒，所以 128 长度必须被判非法、127 才是上限
        assertFalse(PackageNameUtils.isValid("com." + "a".repeat(PackageNameUtils.KERNEL_MAX_LENGTH - 4)))
        assertTrue(PackageNameUtils.isValid("com." + "a".repeat(PackageNameUtils.KERNEL_MAX_LENGTH - 5)))
    }

    @Test
    fun 真实APK改名后清单与签名都成立() {
        assumeTrue("未设置 PKG_RENAME_APK，跳过真实 APK 用例", apkPath != null && apkPath.isNotBlank())
        val apk = File(apkPath!!)
        assumeTrue("被测 APK 不存在：$apkPath", apk.isFile)

        val key = generatedKey()
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
        // verifyReplaced 返回 null 表示通过，否则是残留描述
        val leftover = ManifestPackageRewriter.verifyReplaced(outManifest, oldPackage, newPackage)
        org.junit.Assert.assertNull("改名后清单应彻底换掉旧包名，残留：$leftover", leftover)
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
