package com.sukisu.ultra.ui.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ThemeMediaCryptoTest {
    private fun seal(plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(ThemeMediaCrypto.keyBytes, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val sealed = cipher.doFinal(plain)
        return "SKM1".toByteArray() +
            nonce +
            ByteBuffer.allocate(8).putLong(plain.size.toLong()).array() +
            sealed
    }

    @Test
    fun recognizesSealedHeader() {
        assertTrue(ThemeMediaCrypto.isSealed("SKM1".toByteArray()))
        assertTrue(ThemeMediaCrypto.isSealed(seal(ByteArray(0)).copyOfRange(0, 24)))
    }

    @Test
    fun rejectsOtherOrShortHeaders() {
        assertFalse(ThemeMediaCrypto.isSealed(ByteArray(0)))
        assertFalse(ThemeMediaCrypto.isSealed("SKM".toByteArray()))
        assertFalse(ThemeMediaCrypto.isSealed(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)))
    }

    @Test
    fun decryptsSealedMedia() {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val sealed = seal(payload)

        assertEquals(payload.size.toLong(), ThemeMediaCrypto.plainLength(sealed))
        assertArrayEquals(payload, ThemeMediaCrypto.decrypt(sealed))
    }

    @Test
    fun emptyMediaRoundTrips() {
        assertArrayEquals(ByteArray(0), ThemeMediaCrypto.decrypt(seal(ByteArray(0))))
    }

    @Test
    fun rejectsTamperedPayload() {
        val sealed = seal("sealed preview bytes".toByteArray())
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0x01).toByte()

        assertTrue(runCatching { ThemeMediaCrypto.decrypt(sealed) }.isFailure)
    }
}
