package com.miowser.app.browser

import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.MediaUrlClassifier

/**
 * A download handed to Miowser by WebView after a page link or server response declares that
 * its payload should be downloaded. This is separate from grabber detections: the user already
 * asked the website for this file, so BrowserScreen can enqueue it immediately with the same
 * user-agent, cookies, and referrer as the originating tab.
 */
data class WebDownloadRequest(
    val originatingTabId: Long,
    val url: String,
    val pageUrl: String,
    val pageTitle: String,
    val userAgent: String,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long
)

/** Only video responses that Media3 can open get a Stream action. */
internal fun streamableDownloadType(request: WebDownloadRequest, suggestedName: String): MediaType? {
    val type = MediaUrlClassifier.classifyByMimeType(request.mimeType)
        ?: MediaUrlClassifier.classify(request.url)
        ?: MediaUrlClassifier.classify(suggestedName)
    return type?.takeIf {
        it == MediaType.HLS || it == MediaType.DASH || it == MediaType.MP4 ||
            it == MediaType.WEBM || it == MediaType.MATROSKA
    }
}
