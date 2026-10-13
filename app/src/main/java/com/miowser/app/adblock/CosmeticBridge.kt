package com.miowser.app.adblock

import android.webkit.JavascriptInterface
import com.miowser.app.settings.AppSettings

/** Public CSS only. Called by the page's deferred cosmetic scheduler, never for network matching. */
class CosmeticBridge(private val engine: AdBlockEngine) {
    @JavascriptInterface
    fun cssFor(hostname: String?): String {
        if (!AppSettings.current.adBlockEnabled || hostname == null || hostname.length > 253) return ""
        return engine.cosmeticCssFor(hostname?.lowercase().orEmpty())
    }
}
