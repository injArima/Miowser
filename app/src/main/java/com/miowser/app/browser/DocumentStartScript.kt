package com.miowser.app.browser

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.miowser.app.adblock.MusicAdProtection

private const val TAG = "Miowser/DocStart"

// Apply hiding at document start so ad elements never get a visible first paint.
private const val COSMETIC_INJECTION_SCRIPT = """
    (function() {
        function applyMiowserCosmetics() {
            try {
                var bridge = window.MiowserAdBlock;
                if (!bridge) return false;
                var css = bridge.cssFor(document.location.hostname);
                var style = document.querySelector('style[data-miowser-adblock="1"]');
                if (!css) {
                    if (style) style.remove();
                    return true;
                }
                var target = document.head || document.documentElement;
                if (!target) return false;
                if (!style) {
                    style = document.createElement('style');
                    style.setAttribute('data-miowser-adblock', '1');
                    target.appendChild(style);
                }
                if (style.textContent !== css) style.textContent = css;
                return true;
            } catch (e) {
                return true;
            }
        }

        if (!applyMiowserCosmetics()) {
            var observer = new MutationObserver(function() {
                if (applyMiowserCosmetics()) observer.disconnect();
            });
            observer.observe(document, { childList: true, subtree: true });
        }
    })();
"""

/** Registers the trusted Music resource before page JS, followed by general cosmetic CSS. */
fun WebView.installCosmeticInjectionScript() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(this, MusicAdProtection.documentStartScript, setOf("https://music.youtube.com"))
        }.onFailure {
            com.miowser.app.diagnostics.SafeLog.warning(TAG, "Music document-start resource unavailable; using page-finish fallback")
        }
        WebViewCompat.addDocumentStartJavaScript(this, COSMETIC_INJECTION_SCRIPT, setOf("*"))
    } else {
        com.miowser.app.diagnostics.SafeLog.warning(TAG, "DOCUMENT_START_SCRIPT not supported by this WebView provider — using page-finish cosmetic scheduling")
    }
}

/** Refreshes/removes CSS and reversible Music hooks on an open page after policy changes. */
fun WebView.applyCosmeticFiltersNow() {
    evaluateJavascript(MusicAdProtection.documentStartScript, null)
    evaluateJavascript(COSMETIC_INJECTION_SCRIPT, null)
}
