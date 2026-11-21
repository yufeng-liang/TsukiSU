package com.sukisu.ultra.core.pkgrename

import android.content.Context
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** 内置密钥读取失败。 */
class ManagerKeyException(message: String) : Exception(message)

/**
 * 内置的重签密钥。
 *
 * 为什么要把私钥放进 APK：内核认主有两条路。主通道是动态管理器白名单（不看证书），
 * 副通道是「签名兜底」——内核里编译期写死了官方管理器证书的尺寸与 SHA-256，
 * 只要 APK 用同一张证书重签，无论包名叫什么都能被加冕。把官方密钥内置进来，
 * 换包名后重签就能自动走通副通道，用户不必再手工登记。
 *
 * 代价是私钥随 APK 分发，等于公开。这一点在设计文档第 7 节已明确接受。
 * **当前实现是明文**：PKCS#8 与证书 DER 以 asset 形式随 APK 下发，任何拿到安装包的人
 * 都能用 `unzip` + `openssl` 导出。加密存放（设备绑定密钥）与「关闭通道 B」的开关
 * 尚未实现，属于后续工作，不要在文档或 UI 里宣称已经加密。
 */
internal data class ManagerKeyMaterial(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val certDer: ByteArray,
    val certSha256: ByteArray,
) {
    /** 证书 SHA-256 的十六进制小写形式（内核 hash 字段就是它）。 */
    val certSha256Hex: String = certSha256.joinToString("") { b ->
        val v = b.toInt() and 0xFF
        "0123456789abcdef"[v ushr 4].toString() + "0123456789abcdef"[v and 0x0F]
    }

    /** 与内核 `KSU_EXPECTED_SIZE` 对齐的证书尺寸。 */
    val certSize: Int get() = certDer.size
}

internal object ManagerKeyProvider {

    /** 资产路径：与 `app/src/main/assets/pkgrename/` 对应。 */
    private const val ASSET_KEY = "pkgrename/manager-key.pk8"
    private const val ASSET_CERT = "pkgrename/manager-cert.der"

    @Volatile
    private var cached: ManagerKeyMaterial? = null

    /** 读取（并缓存）内置密钥。 */
    fun get(context: Context): ManagerKeyMaterial {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val appContext = context.applicationContext
            val pk8 = readAsset(appContext, ASSET_KEY)
            val der = readAsset(appContext, ASSET_CERT)
            return parse(pk8, der).also { cached = it }
        }
    }

    /** 清掉缓存（切换/关闭副通道时用）。 */
    fun invalidate() {
        synchronized(this) { cached = null }
    }

    /**
     * 从原始字节解析。抽出来是为了让 JVM 单测能绕开 Android Context 直接喂文件。
     */
    fun parse(pk8: ByteArray, certDer: ByteArray): ManagerKeyMaterial {
        val key = try {
            KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(pk8))
        } catch (e: Exception) {
            throw ManagerKeyException("解析私钥失败：${e.message}")
        }
        val cert = try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(certDer.inputStream()) as X509Certificate
        } catch (e: Exception) {
            throw ManagerKeyException("解析证书失败：${e.message}")
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(certDer)
        return ManagerKeyMaterial(key, cert, certDer, sha)
    }

    private fun readAsset(context: Context, name: String): ByteArray {
        return try {
            context.assets.open(name).use { it.readBytes() }
        } catch (e: Exception) {
            throw ManagerKeyException("读取内置密钥失败：$name（${e.message}）")
        }
    }
}
