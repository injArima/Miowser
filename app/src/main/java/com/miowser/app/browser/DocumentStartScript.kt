package com.miowser.app.browser

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val TAG = "Miowser/DocStart"

// Runs before any page script (see installCosmeticInjectionScript doc). Reads this page's
// cosmetic CSS from the native AdBlockEngine via a synchronous JavascriptInterface call and
// injects it as a single <style> tag. CSS-based hiding (rather than a MutationObserver +
// manual DOM removal, which is closer to what 1DM's engine does) naturally covers elements
// added to the page later too, since the cascade applies live — no sweep pass needed.
private const val COSMETIC_INJECTION_SCRIPT = """
    (function() {
        try {
            var bridge = window.MiowserAdBlock;
            if (!bridge) return;
            var css = bridge.cssFor(document.location.hostname);
            if (!css) return;
            var style = document.createElement('style');
            style.setAttribute('data-miowser-adblock', '1');
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);
        } catch (e) {}
    })();
"""

/**
 * Installs the cosmetic-filter injection script via androidx.webkit's
 * addDocumentStartJavaScript, guaranteed to run before any page script — the real fix for
 * cosmetic-filter timing races (see project memory: miowser-browser-engine-decision.md), and
 * verified in practice to run ~950ms before a real page's own scripts during the earlier
 * interception-pipeline proof. Feature-detected since it's only available on newer WebView
 * provider versions. Must be called before loadUrl() for the first navigation to pick it up,
 * and requires a CosmeticBridge already registered as "MiowserAdBlock" on this WebView.
 */
fun WebView.installCosmeticInjectionScript() {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        Log.w(TAG, "DOCUMENT_START_SCRIPT not supported by this WebView provider — cosmetic filtering degraded")
        return
    }
    WebViewCompat.addDocumentStartJavaScript(this, COSMETIC_INJECTION_SCRIPT, setOf("*"))
}
