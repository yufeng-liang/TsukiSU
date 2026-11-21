package com.sukisu.ultra.core.pkgrename

import android.content.Context
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** 密钥读取失败。 */
class ManagerKeyException(message: String) : Exception(message)

/**
 * 用来给改名后的 APK 重签的密钥材料。
 *
 * 密钥是**每台设备现场生成**的，不再随 APK 分发（见 LocalSigningKeyProvider 的说明）：
 * 内置官方私钥等于把管理器印章发给所有人，而换包名本来就不需要官方私钥——
 * 新包名是一次全新安装，签名自洽即可；内核侧的授权走动态管理器白名单（按 uid）。
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

    /** 与内核 KSU_EXPECTED_SIZE 对齐的证书尺寸。 */
    val certSize: Int get() = certDer.size
}

/** 密钥入口：读取本机密钥（首次生成），并提供纯字节解析供单测使用。 */
internal object ManagerKeyProvider {

    /** 本机密钥；首次调用会生成 RSA 2048 并写盘，界面侧必须在 IO 线程调用。 */
    fun get(context: Context): ManagerKeyMaterial = LocalSigningKeyProvider.get(context)

    /** 清掉缓存（密钥重建后调用）。 */
    fun invalidate() {
        LocalSigningKeyProvider.invalidate()
    }

    /**
     * 从原始字节解析。抽出来是为了让 JVM 单测能绕开 Android Context 直接喂文件。
     */
    fun parse(pk8: ByteArray, certDer: ByteArray): ManagerKeyMaterial {
        val key = try {
            KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(pk8))
        } catch (e: Exception) {
            throw ManagerKeyException("解析私钥失败：" + e.message)
        }
        val cert = try {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(certDer.inputStream()) as X509Certificate
        } catch (e: Exception) {
            throw ManagerKeyException("解析证书失败：" + e.message)
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(certDer)
        return ManagerKeyMaterial(key, cert, certDer, sha)
    }
}
