package com.miowser.app.adblock

import java.io.InputStream
import java.security.MessageDigest

/** Content identities of immutable shipped inputs, read once per process, never on request paths. */
internal class FilterCacheIdentity(private val load: () -> List<Pair<String, () -> InputStream>>) {
    val digest: String by lazy {
        fingerprint(load().map { (name, open) ->
            val hash = MessageDigest.getInstance("SHA-256")
            open().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            "$name:${hex(hash.digest())}"
        })
    }

    companion object {
        // Bump parser compatibility when rule semantics change, independently of APK versions.
        const val PARSER_COMPATIBILITY = "parser-4-baseline-1"
        const val SNAPSHOT_COMPATIBILITY = "snapshot-format-4"
        fun subscriptionParts(row: FilterSubscription, extension: String?, bytes: Long, modified: Long): List<String> =
            if (!row.enabled) emptyList() else listOf(row.id, row.url, row.isBuiltIn.toString(), "$extension:$bytes:$modified")

        fun fingerprint(parts: List<String>): String {
            val hash = MessageDigest.getInstance("SHA-256")
            parts.forEach {
                val bytes = it.toByteArray(Charsets.UTF_8)
                hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                hash.update(bytes)
            }
            return hex(hash.digest())
        }
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
