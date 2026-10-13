package com.miowser.app.downloader

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS segment decryption per RFC 8216 §5.2: AES-128 in CBC mode with PKCS7 padding. The key is
 * the raw 16 bytes fetched from the playlist's #EXT-X-KEY URI; the IV is either explicit (the
 * KEY tag's IV=0x... attribute) or, when the tag omits it, the segment's own media-sequence
 * number encoded as a big-endian 128-bit value — this fallback is mandated by the spec, not a
 * guess, and 1DM's own m3u8DecryptionKeyRequired/IV handling (project memory:
 * 1dm-grabber-mechanics.md) confirms the same real-world necessity of getting it right.
 */
object HlsAes128Decryptor {
    fun decrypt(ciphertext: ByteArray, key: ByteArray, ivHex: String?, sequenceNumber: Long): ByteArray {
        val cipher = cipher(key, ivHex, sequenceNumber)
        return cipher.doFinal(ciphertext)
    }

    internal fun cipher(key: ByteArray, ivHex: String?, sequenceNumber: Long): Cipher {
        require(key.size == 16) { "HLS AES-128 key must contain exactly 16 bytes" }
        val iv = ivHex?.let { hexToBytes(it) } ?: sequenceNumberToIv(sequenceNumber)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher
    }

    internal fun sequenceNumberToIv(sequenceNumber: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0 until 8) {
            iv[15 - i] = ((sequenceNumber shr (i * 8)) and 0xFF).toByte()
        }
        return iv
    }

    internal fun hexToBytes(hex: String): ByteArray {
        val stripped = hex.removePrefix("0x").removePrefix("0X")
        val padded = if (stripped.length % 2 == 1) "0$stripped" else stripped
        return ByteArray(padded.length / 2) { i ->
            padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
