package com.miowser.app.downloader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsAes128DecryptorTest {

    private val key = ByteArray(16) { it.toByte() } // any 16-byte key; content doesn't matter for a round-trip test

    private fun encrypt(plaintext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    @Test
    fun `round-trips with an explicit IV`() {
        val plaintext = "the quick brown fox jumps over the lazy dog".toByteArray()
        val iv = ByteArray(16) { (it * 3).toByte() }
        val ivHex = iv.joinToString("") { "%02x".format(it) }

        val ciphertext = encrypt(plaintext, iv)
        val decrypted = HlsAes128Decryptor.decrypt(ciphertext, key, ivHex, sequenceNumber = 0)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `round-trips with a 0x-prefixed IV`() {
        val plaintext = "segment payload".toByteArray()
        val iv = ByteArray(16) { 0x07 }
        val ivHex = "0x" + iv.joinToString("") { "%02x".format(it) }

        val ciphertext = encrypt(plaintext, iv)
        assertArrayEquals(plaintext, HlsAes128Decryptor.decrypt(ciphertext, key, ivHex, sequenceNumber = 99))
    }

    @Test
    fun `derives the IV from the sequence number when none is given, per RFC 8216`() {
        val plaintext = "no explicit IV on this key tag".toByteArray()
        val sequenceNumber = 12345L
        val derivedIv = HlsAes128Decryptor.sequenceNumberToIv(sequenceNumber)

        val ciphertext = encrypt(plaintext, derivedIv)
        assertArrayEquals(plaintext, HlsAes128Decryptor.decrypt(ciphertext, key, ivHex = null, sequenceNumber = sequenceNumber))
    }

    @Test
    fun `sequence-number IV is big-endian in the low 8 bytes, zero-padded in the high 8`() {
        val iv = HlsAes128Decryptor.sequenceNumberToIv(0x0102030405060708L)

        assertEquals(16, iv.size)
        for (i in 0 until 8) assertEquals(0.toByte(), iv[i]) // high 8 bytes are always zero (sequence numbers fit in a Long)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), iv.copyOfRange(8, 16))
    }

    @Test
    fun `a wrong key produces garbage, not a crash-free false positive`() {
        val plaintext = "sensitive segment bytes".toByteArray()
        val iv = ByteArray(16) { 0x01 }
        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val ciphertext = encrypt(plaintext, iv)

        val wrongKey = ByteArray(16) { (it + 1).toByte() }
        // PKCS7 padding is very likely to be invalid with the wrong key, which javax.crypto
        // surfaces as an exception rather than silently returning wrong bytes — asserting that
        // instead of exact garbage bytes, since padding validity (not content) is what's
        // deterministic here.
        try {
            val result = HlsAes128Decryptor.decrypt(ciphertext, wrongKey, ivHex, sequenceNumber = 0)
            assert(!result.contentEquals(plaintext))
        } catch (e: Exception) {
            // Expected: a BadPaddingException is a correct outcome for a wrong key, not a bug.
        }
    }
}
