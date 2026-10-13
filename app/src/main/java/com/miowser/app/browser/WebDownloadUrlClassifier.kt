package com.miowser.app.browser

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Recognizes ordinary non-renderable files before WebView commits a main-frame navigation.
 * This catches JavaScript/countdown redirects to APKs and archives that some WebView builds do
 * not forward to DownloadListener at all.
 */
object WebDownloadUrlClassifier {
    private val DOWNLOAD_EXTENSIONS = setOf(
        "apk", "apks", "xapk", "aab",
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
        "pdf", "epub", "mobi", "torrent", "iso",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "exe", "msi", "dmg", "deb", "rpm"
    )

    fun isLikelyDownload(url: String): Boolean {
        val decoded = runCatching {
            URLDecoder.decode(url, StandardCharsets.UTF_8.name())
        }.getOrDefault(url).lowercase()
        val withoutFragment = decoded.substringBefore('#')
        val pathExtension = withoutFragment.substringBefore('?').trimEnd('/').substringAfterLast('.', "")
        if (pathExtension in DOWNLOAD_EXTENSIONS) return true

        // Covers endpoints such as /download?file=release.apk without treating every unrelated
        // query containing a dot as a download.
        return Regex("(?:^|[?&])(?:file|filename|download|url|target)=[^&#]*\\.([a-z0-9]{1,10})(?:$|[&#])")
            .find(withoutFragment)
            ?.groupValues
            ?.getOrNull(1) in DOWNLOAD_EXTENSIONS
    }
}
