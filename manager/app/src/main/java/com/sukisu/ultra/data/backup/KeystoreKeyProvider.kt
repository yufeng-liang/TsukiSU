package com.sukisu.ultra.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeystoreKeyProvider {

    /** WebDAV 凭据的默认别名；其他凭据（上传会话令牌等）用各自的别名，互不连坐。 */
    private const val ALIAS = "sukisu_backup_webdav"
    private const val PROVIDER = "AndroidKeyStore"

    /**
     * 取（或生成）指定别名的 AES-256/GCM 密钥；不传别名即 WebDAV 那一把。
     *
     * 调用方拿到的是**自己别名下**的密钥：某把密钥被系统清掉（极少见）不牵动别的
     * 凭据 —— 各凭据各自降级重验，互不影响。
     */
    @Synchronized
    fun key(alias: String = ALIAS): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
