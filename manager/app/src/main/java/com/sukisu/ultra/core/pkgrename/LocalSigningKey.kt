package com.sukisu.ultra.core.pkgrename

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 每台设备**现场生成**的重签密钥。
 *
 * 为什么不内置官方私钥：内置等于把「管理器印章」随 APK 分发出去，谁解压都能拿它签一个
 * 任意包名的 App，装到把该证书填进内核签名槽的手机上就会被当成管理器（root 权限）。
 * 而换包名根本不需要官方私钥——新包名是一次全新安装，Android 只要求签名自洽，
 * 不要求与任何既有证书相同。所以每台设备自己生成一对 RSA 密钥、自己签一张证书就够了，
 * 内核那边靠「动态管理器白名单」按 uid 授权（不校验证书哈希），这条链对每个用户都成立。
 *
 * 存放：`filesDir/pkgrename-keys/signing-key.bin`，AES-GCM 加密，密钥是 Android Keystore 里
 * 不可导出的 AES-256（别名 `pkgrename_key_wrap`）。Keystore 不可用时退化为明文私有文件
 * （仍然是 App 私有目录，只有 root 能读），并在文件头记版本号区分。
 */
internal object LocalSigningKeyProvider {

    private const val DIR = "pkgrename-keys"
    private const val FILE_NAME = "signing-key.bin"
    private const val WRAP_ALIAS = "pkgrename_key_wrap"
    private const val VERSION_PLAIN = 0
    private const val VERSION_WRAP = 1
    private const val GCM_IV_SIZE = 12

    @Volatile
    private var cached: ManagerKeyMaterial? = null

    /** 读取（首次会生成）本机签名密钥。首次调用会做 RSA 2048 生成，必须在 IO 线程调用。 */
    fun get(context: Context): ManagerKeyMaterial {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val appContext = context.applicationContext
            val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, FILE_NAME)
            val material = if (file.isFile) {
                runCatching { read(file) }.getOrElse {
                    // 文件损坏（掉电、手改）时不静默降级成「另一个身份」：直接重建，旧产物随之作废。
                    file.delete()
                    create(appContext, file)
                }
            } else {
                create(appContext, file)
            }
            cached = material
            return material
        }
    }

    fun invalidate() {
        synchronized(this) { cached = null }
    }

    private fun create(appContext: Context, file: File): ManagerKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cn = "SukiSU Manager Local " + SecureRandom().let { r ->
            val b = ByteArray(4).also { r.nextBytes(it) }
            b.joinToString("") { "%02x".format(it) }
        }
        val certDer = X509SelfSigned.build(pair, cn)
        val material = ManagerKeyProvider.parse(pair.private.encoded, certDer)
        write(appContext, file, pair.private.encoded, certDer)
        return material
    }

    private fun write(appContext: Context, file: File, pk8: ByteArray, certDer: ByteArray) {
        val payload = ByteArrayOutputStream().apply {
            writeU32(pk8.size)
            write(pk8)
            writeU32(certDer.size)
            write(certDer)
        }.toByteArray()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            val wrapKey = runCatching { wrapKey() }.getOrNull()
            val bytes = if (wrapKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
                byteArrayOf(VERSION_WRAP.toByte()) + cipher.iv + cipher.doFinal(payload)
            } else {
                byteArrayOf(VERSION_PLAIN.toByte()) + payload
            }
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw ManagerKeyException("写入本机密钥失败")
            }
        } finally {
            tmp.delete()
        }
    }

    private fun read(file: File): ManagerKeyMaterial {
        val bytes = FileInputStream(file).use { it.readBytes() }
        if (bytes.size < 2) throw ManagerKeyException("本机密钥文件为空")
        val payload = when (bytes[0].toInt()) {
            VERSION_PLAIN -> bytes.copyOfRange(1, bytes.size)
            VERSION_WRAP -> {
                val wrapKey = wrapKey()
                val iv = bytes.copyOfRange(1, 1 + GCM_IV_SIZE)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
                cipher.doFinal(bytes, 1 + GCM_IV_SIZE, bytes.size - 1 - GCM_IV_SIZE)
            }
            else -> throw ManagerKeyException("不认识的本机密钥版本：${bytes[0]}")
        }
        var p = 0
        val keyLen = readU32(payload, p); p += 4
        if (keyLen <= 0 || p + keyLen > payload.size) throw ManagerKeyException("本机密钥长度异常")
        val pk8 = payload.copyOfRange(p, p + keyLen); p += keyLen
        val certLen = readU32(payload, p); p += 4
        if (certLen <= 0 || p + certLen > payload.size) throw ManagerKeyException("本机证书长度异常")
        val certDer = payload.copyOfRange(p, p + certLen)
        return ManagerKeyProvider.parse(pk8, certDer)
    }

    /** Android Keystore 里不可导出的 AES-256 包装密钥，首次使用时创建。 */
    private fun wrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(WRAP_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArrayOutputStream.writeU32(v: Int) {
        write(v and 0xFF); write(v ushr 8 and 0xFF); write(v ushr 16 and 0xFF); write(v ushr 24 and 0xFF)
    }

    private fun readU32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)
}
