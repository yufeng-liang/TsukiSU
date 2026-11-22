package com.sukisu.ultra.ui.util

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Opens the CNB media envelope (`SKM1`) used for previews, screenshots and
 * avatars stored in the public theme repository.
 *
 * The manager carries the media key because it opts into direct CNB downloads:
 * sealed bytes travel from CNB to the app, so the server no longer streams the
 * images itself. Browsers never send the direct marker and keep receiving
 * decrypted bytes; direct messages are always served by the service.
 */
object ThemeMediaCrypto {
    private val MAGIC = byteArrayOf(
        'S'.code.toByte(), 'K'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte(),
    )
    private const val IV_SIZE = 12
    private const val LENGTH_SIZE = 8
    const val HEADER_SIZE = 4 + IV_SIZE + LENGTH_SIZE
    private const val TAG_BIT_LENGTH = 128

    // Hex rather than Base64 keeps the constant JVM-testable on every API level.
    private const val KEY_HEX =
        "13c4b9e823e7bce77af4b5e7add9af20f773cf51c25e968a8ab830f905113b31"

    internal val keyBytes: ByteArray = ByteArray(KEY_HEX.length / 2) { index ->
        KEY_HEX.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
    private val secretKey = SecretKeySpec(keyBytes, "AES")

    fun isSealed(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && MAGIC.indices.all { prefix[it] == MAGIC[it] }

    fun plainLength(header: ByteArray): Long {
        require(header.size >= HEADER_SIZE) { "Invalid sealed media header" }
        return ByteBuffer.wrap(header, 16, LENGTH_SIZE).long
    }

    /**
     * Decrypts a complete sealed body. Images are small, so buffering keeps the
     * interceptor simple and lets the GCM tag cover the whole payload.
     */
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size >= HEADER_SIZE) { "Invalid sealed media payload" }
        require(isSealed(bytes)) { "Invalid sealed media header" }
        val iv = bytes.copyOfRange(4, 16)
        val declared = plainLength(bytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BIT_LENGTH, iv))
        val plain = try {
            cipher.doFinal(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE)
        } catch (error: GeneralSecurityException) {
            throw IllegalStateException("Sealed media authentication failed", error)
        }
        require(plain.size.toLong() == declared) { "Sealed media length mismatch" }
        return plain
    }
}
