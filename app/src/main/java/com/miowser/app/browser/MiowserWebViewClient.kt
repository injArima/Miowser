package com.miowser.app.browser

import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

private const val TAG = "Miowser/Intercept"

// The classic 1x1 transparent GIF — a valid-but-inert stub of the right MIME type, not a bare
// failure, matching the substitution pattern found in 1DM's ad-block engine (see project
// memory: 1dm-adblock-engine.md).
private val TRANSPARENT_GIF_PIXEL: ByteArray = Base64.decode(
    "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7",
    Base64.DEFAULT
)

private val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp")

/**
 * Phase-1 proof-of-concept interceptor — NOT the real ad-block engine. It logs every request
 * that passes through shouldInterceptRequest (proving real interception of every subresource,
 * not just the main document navigation) and substitutes an inert stub for image requests
 * specifically, as a visible, screenshot-verifiable demonstration that response substitution
 * actually works end-to-end against a real site before anything is built on top of it.
 */
class MiowserWebViewClient(
    private val onCanGoBackChanged: (Boolean) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        Log.d(TAG, "request: ${request.method} $url (mainFrame=${request.isForMainFrame})")

        val isImage = IMAGE_EXTENSIONS.any { ext ->
            url.substringBefore('?').endsWith(ext, ignoreCase = true)
        }
        if (isImage) {
            Log.d(TAG, "blocking image, substituting inert stub: $url")
            return WebResourceResponse("image/gif", null, ByteArrayInputStream(TRANSPARENT_GIF_PIXEL))
        }
        return null // fall through to normal loading
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onCanGoBackChanged(view.canGoBack())
    }
}
