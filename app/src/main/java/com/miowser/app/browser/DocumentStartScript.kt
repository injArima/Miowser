package com.miowser.app.browser

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val TAG = "Miowser/DocStart"

// Proof script: logs a timestamp (independently verifiable via logcat ordering against the
// page's own load-finish timestamp) and inserts a visible banner at the very top of the page —
// something a page's own scripts could not have removed if this genuinely ran first.
private val PROOF_SCRIPT = """
    (function() {
        console.log('[Miowser] document-start script executed at ' + Date.now());
        function insertBanner() {
            if (document.getElementById('miowser-proof-banner')) return;
            var banner = document.createElement('div');
            banner.id = 'miowser-proof-banner';
            banner.textContent = 'Miowser: injected before page scripts ran';
            banner.style.cssText = 'position:relative;z-index:999999;background:#ff3b30;' +
                'color:#fff;padding:12px;font:16px sans-serif;text-align:center;';
            document.body.insertBefore(banner, document.body.firstChild);
        }
        if (document.body) {
            insertBanner();
        } else {
            document.addEventListener('DOMContentLoaded', insertBanner);
        }
    })();
""".trimIndent()

/**
 * Installs a script guaranteed to run before any page script, via androidx.webkit's
 * addDocumentStartJavaScript — the real fix for cosmetic-filter injection timing races (see
 * project memory: miowser-browser-engine-decision.md). Feature-detected since it's only
 * available on newer WebView provider versions; must be called before loadUrl() for the
 * upcoming navigation to pick it up.
 */
fun WebView.installDocumentStartProofScript() {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        Log.w(TAG, "DOCUMENT_START_SCRIPT not supported by this WebView provider — skipping")
        return
    }
    WebViewCompat.addDocumentStartJavaScript(this, PROOF_SCRIPT, setOf("*"))
}
