package com.miowser.app.browser.media

import java.net.URI

internal object MediaArtworkPolicy {
    const val targetSize = 512
    const val maxEncodedBytes = 2 * 1024 * 1024
    const val cacheBytes = 4 * 1024 * 1024
    const val minCacheCharge = 512 * 1024
    const val attempts = 2
    const val retryDelayMs = 1000L

    /** Only an origin is sent; never a page path, query, userinfo, cookie, or HTTPS downgrade. */
    fun referrer(pageUrl: String, artworkUrl: String): String? = runCatching {
        val page = URI(pageUrl)
        val artwork = URI(artworkUrl)
        if (page.scheme !in listOf("http", "https") || artwork.scheme !in listOf("http", "https") ||
            page.host.isNullOrBlank() || artwork.host.isNullOrBlank() || page.rawUserInfo != null ||
            artwork.rawUserInfo != null || page.scheme == "https" && artwork.scheme != "https") return null
        URI(page.scheme, null, page.host, page.port, "/", null, null).toASCIIString()
    }.getOrNull()

    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > targetSize) sample *= 2
        return sample
    }
}
