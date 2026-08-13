package com.miowser.app.adblock

/**
 * Coarse resource-type classification for a request, inferred at interception time. Android's
 * WebResourceRequest doesn't expose the resource type the way desktop extension APIs
 * (webRequest) do, so this is reconstructed from the Sec-Fetch-Dest header — sent by
 * Chromium-based WebView on modern devices — with a fallback to the Accept header and URL
 * extension for older providers that don't set it.
 */
enum class ResourceType {
    DOCUMENT,
    SUBDOCUMENT, // iframe
    SCRIPT,
    IMAGE,
    STYLESHEET,
    XHR,
    FONT,
    MEDIA,
    WEBSOCKET,
    OTHER;

    companion object {
        private val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".bmp")
        private val FONT_EXTENSIONS = listOf(".woff", ".woff2", ".ttf", ".otf", ".eot")

        fun infer(url: String, headers: Map<String, String>, isMainFrame: Boolean): ResourceType {
            if (isMainFrame) return DOCUMENT

            when (headerValue(headers, "Sec-Fetch-Dest")) {
                "document" -> return DOCUMENT
                "iframe", "frame" -> return SUBDOCUMENT
                "script" -> return SCRIPT
                "image" -> return IMAGE
                "style" -> return STYLESHEET
                "font" -> return FONT
                "audio", "video" -> return MEDIA
                "empty" -> return XHR // fetch()/XHR report their dest as "empty"
            }

            val accept = headerValue(headers, "Accept").orEmpty()
            if (accept.contains("text/css")) return STYLESHEET
            if (accept.contains("image/")) return IMAGE

            val urlNoQuery = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                urlNoQuery.startsWith("ws://") || urlNoQuery.startsWith("wss://") -> WEBSOCKET
                urlNoQuery.endsWith(".js") || urlNoQuery.endsWith(".mjs") -> SCRIPT
                urlNoQuery.endsWith(".css") -> STYLESHEET
                IMAGE_EXTENSIONS.any { urlNoQuery.endsWith(it) } -> IMAGE
                FONT_EXTENSIONS.any { urlNoQuery.endsWith(it) } -> FONT
                else -> OTHER
            }
        }

        private fun headerValue(headers: Map<String, String>, name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.lowercase()
    }
}
