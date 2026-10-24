package com.miowser.app.adblock

import java.net.URI
import java.util.Locale

internal data class NewFilterListUrls(
    val urls: List<String>,
    val duplicates: Int,
    val invalid: Int
)

/** Compare complete list URLs, not hosts: one site may publish several distinct lists. */
internal object FilterSubscriptionUrls {
    fun normalize(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (raw.length > 8_192 || scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.port !in -1..65535) null
        else {
            val host = uri.host.lowercase(Locale.ROOT)
            val port = uri.port.takeUnless { it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            "$scheme://$host${port?.let { ":$it" }.orEmpty()}$path" +
                (uri.rawQuery?.let { "?$it" }.orEmpty())
        }
    }.getOrNull()

    fun selectNew(existingUrls: Iterable<String>, pastedUrls: Iterable<String>): NewFilterListUrls {
        val seen = existingUrls.mapNotNull(::normalize).toMutableSet()
        val novel = ArrayList<String>()
        var duplicates = 0
        var invalid = 0
        pastedUrls.forEach { raw ->
            val url = normalize(raw)
            when {
                url == null -> invalid++
                !seen.add(url) -> duplicates++
                else -> novel += url
            }
        }
        return NewFilterListUrls(novel, duplicates, invalid)
    }
}
