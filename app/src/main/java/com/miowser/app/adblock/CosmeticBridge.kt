package com.miowser.app.adblock

import android.webkit.JavascriptInterface

/**
 * Exposed to page JS as `window.MiowserAdBlock` (via WebView.addJavascriptInterface) so the
 * document-start script can fetch this page's domain-specific cosmetic CSS synchronously and
 * inject it before the page's own scripts run — true document-start cosmetic hiding, not a
 * post-load pass. Safe to expose to arbitrary (untrusted) page content despite the historical
 * addJavascriptInterface risks: the only method here returns non-sensitive CSS text and does
 * nothing dangerous with its input regardless of what a malicious page passes in.
 */
class CosmeticBridge(private val engine: AdBlockEngine) {
    @JavascriptInterface
    fun cssFor(hostname: String?): String = engine.cosmeticCssFor(hostname?.lowercase().orEmpty())
}
