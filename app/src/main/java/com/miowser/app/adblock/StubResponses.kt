package com.miowser.app.adblock

import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Valid-but-inert stand-ins for blocked resources, keyed by resource type — returning the
 * right *shape* of empty response (correct MIME type, and CORS headers so a page's own
 * fetch()/XHR to a blocked endpoint doesn't itself throw a CORS error) rather than a bare
 * failure, so a blocked resource doesn't break the page around it. Pattern validated against
 * 1DM's own ad-block engine (see project memory: 1dm-adblock-engine.md).
 */
object StubResponses {
    private val TRANSPARENT_GIF_PIXEL: ByteArray = Base64.decode(
        "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7",
        Base64.DEFAULT
    )
    private val CORS_HEADERS = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store")
    private const val EMPTY_HTML = "<!DOCTYPE html><html><head></head><body></body></html>"

    fun forType(type: ResourceType): WebResourceResponse = when (type) {
        ResourceType.IMAGE -> WebResourceResponse(
            "image/gif", null, 200, "OK", CORS_HEADERS, ByteArrayInputStream(TRANSPARENT_GIF_PIXEL)
        )
        ResourceType.SCRIPT -> textStub("application/javascript", "")
        ResourceType.STYLESHEET -> textStub("text/css", "")
        ResourceType.XHR -> textStub("application/json", "{}")
        ResourceType.SUBDOCUMENT, ResourceType.DOCUMENT -> textStub("text/html", EMPTY_HTML)
        else -> textStub("text/plain", "")
    }

    fun blockedDocument(rule: String, sources: List<FilterRuleSource> = emptyList()): WebResourceResponse =
        textStub("text/html", blockedDocumentHtml(rule, sources))

    private fun textStub(mime: String, body: String): WebResourceResponse =
        WebResourceResponse(mime, "utf-8", 200, "OK", CORS_HEADERS, ByteArrayInputStream(body.toByteArray()))
}
