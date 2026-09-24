package com.miowser.app.browser

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
