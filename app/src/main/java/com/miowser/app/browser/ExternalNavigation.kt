package com.miowser.app.browser

import android.content.Intent
import android.net.Uri

/** A small allowlist for links that should leave the browser after user confirmation. */
internal object ExternalNavigation {
    private val internalSchemes = setOf("http", "https", "about", "blob")
    private val blockedSchemes = setOf("file", "content", "javascript", "data")

    fun isExternal(url: String): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme != null && scheme !in internalSchemes && scheme !in blockedSchemes
    }

    fun canStayInWebView(url: String): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
        return scheme in internalSchemes
    }

    fun safeIntent(url: String): Intent? = runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme in internalSchemes || scheme in blockedSchemes) return null
        val intent = if (scheme == "intent") {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        // A page must not address a private component or smuggle a selector through an intent URI.
        intent.component = null
        intent.selector = null
        intent.action = Intent.ACTION_VIEW
        intent.flags = 0
        val targetScheme = intent.data?.scheme?.lowercase() ?: return null
        if (targetScheme in setOf("file", "content", "javascript", "data", "about", "blob")) return null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent
    }.getOrNull()
}
