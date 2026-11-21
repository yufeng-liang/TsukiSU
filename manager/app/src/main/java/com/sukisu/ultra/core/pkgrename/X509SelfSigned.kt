package com.sukisu.ultra.core.pkgrename

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 现场生成一张自签名的 X.509 v3 证书（DER 编码），用来给重签后的 APK 配一张「印章样本」。
 *
 * 为什么不用现成库：Android 平台自带 bouncycastle 的加密原语，但不带 `bcpkix` 那套证书构造器；
 * 引入一个只为签一张自签证书的依赖不划算。X.509 的最小结构只有十几个 TLV，手写更可控。
 *
 * **自签证书是合法可安装的**：Android 不要求 APK 的签名证书由 CA 签发，debug 包全是自签的。
 * 它只需要满足两点：结构是合法 X.509；证书里的公钥与签名私钥配对。
 */
internal object X509SelfSigned {

    /** 1.2.840.113549.1.1.11 = sha256WithRSAEncryption（OID 内容，不含 tag/len）。 */
    private val OID_SHA256_RSA = byteArrayOf(
        0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
    )

    /** 2.5.4.3 = commonName。 */
    private val OID_CN = byteArrayOf(0x55, 0x04, 0x03)

    /**
     * 用 [keyPair] 里的公钥与私钥生成自签证书。
     *
     * @param commonName 证书主体名，界面上不展示，只用于结构完整
     */
    fun build(keyPair: KeyPair, commonName: String, now: Long = System.currentTimeMillis()): ByteArray {
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val algorithm = seq(oid(OID_SHA256_RSA), nullValue())
        val name = seq(set(seq(oid(OID_CN), utf8(commonName))))
        // 有效期取 20 年：UTCTime 只到 2049 年，20 年不会溢出。
        val validity = seq(time(now - 24L * 60 * 60 * 1000), time(now + 20L * 365 * 24 * 60 * 60 * 1000))
        val tbs = seq(
            tagged(0xA0, integer(BigInteger.valueOf(2))), // version: v3
            integer(serial),
            algorithm,
            name,
            validity,
            name,
            keyPair.public.encoded, // 已经是 DER 的 SubjectPublicKeyInfo
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        return seq(tbs, algorithm, bitString(signature))
    }

    // ---------------- DER 基础编码 ----------------

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(content.size + 8)
        out.write(tag)
        val len = content.size
        if (len < 0x80) {
            out.write(len)
        } else {
            var v = len
            val bytes = ArrayList<Int>()
            while (v > 0) {
                bytes.add(v and 0xFF)
                v = v ushr 8
            }
            out.write(0x80 or bytes.size)
            for (i in bytes.indices.reversed()) out.write(bytes[i])
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun cat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    private fun seq(vararg parts: ByteArray): ByteArray = tlv(0x30, cat(parts))

    private fun set(vararg parts: ByteArray): ByteArray = tlv(0x31, cat(parts))

    private fun oid(content: ByteArray): ByteArray = tlv(0x06, content)

    private fun integer(v: BigInteger): ByteArray {
        var b = v.toByteArray()
        // BigInteger 已经保证符号位正确（正数且高位为 1 时会自动补 0x00），这里只防 0。
        if (b.isEmpty()) b = byteArrayOf(0)
        return tlv(0x02, b)
    }

    private fun nullValue(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun utf8(s: String): ByteArray = tlv(0x0C, s.toByteArray(Charsets.UTF_8))

    private fun bitString(bytes: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0) + bytes)

    private fun tagged(tag: Int, inner: ByteArray): ByteArray = tlv(tag, inner)

    /** UTCTime（1950–2049）或 GeneralizedTime。 */
    private fun time(millis: Long): ByteArray {
        val date = Date(millis)
        val year = SimpleDateFormat("yyyy", Locale.US).format(date).toInt()
        return if (year in 1950..2049) {
            val fmt = SimpleDateFormat("yyMMddHHmmss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            tlv(0x17, (fmt.format(date) + "Z").toByteArray(Charsets.US_ASCII))
        } else {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            tlv(0x18, (fmt.format(date) + "Z").toByteArray(Charsets.US_ASCII))
        }
    }
}
