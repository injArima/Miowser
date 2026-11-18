package com.miowser.app.adblock

import android.webkit.JavascriptInterface
import com.miowser.app.settings.AppSettings
import java.net.URI

/** Public cosmetic policy only; never exposes network matching or private application APIs. */
class CosmeticBridge(private val engine: AdBlockEngine) {
    @JavascriptInterface
    fun cssFor(hostname: String?): String {
        if (!AppSettings.current.adBlockEnabled || hostname == null || hostname.length > 253) return ""
        return engine.cosmeticCssFor(hostname?.lowercase().orEmpty())
    }

    /** Synchronous document-start policy; JavaScript independently checks the actual page origin. */
    @JavascriptInterface
    fun musicAdProtectionEnabled(host: String?, pageUrl: String?): Boolean {
        if (!AppSettings.current.adBlockEnabled || host == null || pageUrl == null || pageUrl.length > 8192) return false
        if (!host.equals("music.youtube.com", ignoreCase = true)) return false
        val page = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        if (!page.scheme.equals("https", ignoreCase = true) ||
            !page.host.equals("music.youtube.com", ignoreCase = true) ||
            page.rawUserInfo != null || (page.port != -1 && page.port != 443)) return false
        val pageHost = SiteProtection.host(pageUrl) ?: return false
        return SiteProtection.mode(pageHost) != SiteProtectionMode.OFF
    }
}
