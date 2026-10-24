package com.miowser.app.browser

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

// WebSettings.useWideViewPort still honors width=device-width in the page's meta tag.
// Desktop mode needs to replace that value, including tags inserted later by page scripts.
private const val DESKTOP_VIEWPORT_SCRIPT = """
    (function() {
        if (window.top !== window) return;
        function applyViewport() {
            var head = document.head;
            if (!head) return false;
            var metas = head.querySelectorAll('meta[name]');
            var found = false;
            for (var i = 0; i < metas.length; i++) {
                if (metas[i].getAttribute('name').toLowerCase() !== 'viewport') continue;
                found = true;
                if (metas[i].getAttribute('content') !== 'width=980, initial-scale=1') {
                    metas[i].setAttribute('content', 'width=980, initial-scale=1');
                }
            }
            if (!found) {
                var meta = document.createElement('meta');
                meta.setAttribute('name', 'viewport');
                meta.setAttribute('content', 'width=980, initial-scale=1');
                head.appendChild(meta);
            }
            return true;
        }
        if (applyViewport()) {
            var observer = new MutationObserver(applyViewport);
            observer.observe(document.head, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['name', 'content']
            });
        } else {
            var headObserver = new MutationObserver(function() {
                if (!applyViewport()) return;
                headObserver.disconnect();
                var observer = new MutationObserver(applyViewport);
                observer.observe(document.head, {
                    childList: true, subtree: true, attributes: true,
                    attributeFilter: ['name', 'content']
                });
            });
            headObserver.observe(document, { childList: true, subtree: true });
        }
    })();
"""

/** Install before loading a desktop page; removal applies to the next navigation. */
internal fun WebView.installDesktopViewportScript(): ScriptHandler? =
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, DESKTOP_VIEWPORT_SCRIPT, setOf("*"))
    } else {
        null
    }

/** Fallback for older WebView providers without document-start scripts. */
internal fun WebView.applyDesktopViewportNow() {
    evaluateJavascript(DESKTOP_VIEWPORT_SCRIPT, null)
}
