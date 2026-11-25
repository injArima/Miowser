package com.miowser.app.downloader

import java.io.InputStream
import java.security.MessageDigest

data class FileHashes(val sha256: String, val md5: String, val bytes: Long) {
    fun matches(expected: String): Boolean? {
        val value = expected.trim().lowercase()
        return when {
            value.matches(Regex("[0-9a-f]{64}")) -> value == sha256
            value.matches(Regex("[0-9a-f]{32}")) -> value == md5
            else -> null
        }
    }
}

object FileVerification {
    /** Hashes the actual saved file in bounded memory. The caller owns and closes the stream. */
    fun hash(input: InputStream, checkActive: () -> Unit = {}): FileHashes {
        val sha = MessageDigest.getInstance("SHA-256")
        val md5 = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(64 * 1024)
        var bytes = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            sha.update(buffer, 0, count)
            md5.update(buffer, 0, count)
            bytes += count
        }
        checkActive()
        fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
        return FileHashes(sha.digest().hex(), md5.digest().hex(), bytes)
    }
}
