package com.miowser.app.player

import java.net.URI

/** Browser-default referrer for older Library entries that have no captured WebView header. */
internal fun mediaReferer(pageUrl: String, mediaUrl: String, captured: String?): String? {
    if (!captured.isNullOrBlank()) return captured
    val page = runCatching { URI(pageUrl) }.getOrNull() ?: return null
    val media = runCatching { URI(mediaUrl) }.getOrNull() ?: return null
    val pageScheme = page.scheme?.lowercase() ?: return null
    val mediaScheme = media.scheme?.lowercase() ?: return null
    if (pageScheme !in setOf("http", "https") || mediaScheme !in setOf("http", "https")) return null
    if (pageScheme == "https" && mediaScheme == "http") return null
    val host = page.host ?: return null
    val pagePort = if (page.port < 0) if (pageScheme == "https") 443 else 80 else page.port
    val mediaPort = if (media.port < 0) if (mediaScheme == "https") 443 else 80 else media.port
    val sameOrigin = pageScheme == mediaScheme &&
        host.equals(media.host, ignoreCase = true) && pagePort == mediaPort
    return if (sameOrigin) pageUrl.substringBefore('#')
    else runCatching { URI(pageScheme, null, host, page.port, "/", null, null).toASCIIString() }
        .getOrNull()
}
