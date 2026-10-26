package com.miowser.app.grabber

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val TAG = "Miowser/DocStart"

// Runs before any page script (same document-start guarantee used for cosmetic filtering — see
// DocumentStartScript.kt). Reports every <video>/<audio> element's current src (or a blob: URL
// for MSE-based players) plus duration/dimensions once known, both at scan time and for
// elements added later via a MutationObserver — the "including dynamically-added ones"
// requirement validated against 1DM's own grabber (project memory: 1dm-grabber-mechanics.md).
private const val VIDEO_SNIFFER_SCRIPT = """
    (function() {
        try {
            if (window.__miowserGrabberInstalled) return;
            window.__miowserGrabberInstalled = true;
            var bridge = window.MiowserGrabber;
            if (!bridge) return;

            var reported = new Set();
            var watched = new WeakSet();
            var playing = new Set();
            var token = bridge.documentToken();
            function report(el) {
                var src = el.currentSrc || el.src;
                if (!src || src.length > 8192 || location.href.length > 8192) return;
                var poster = el.tagName.toLowerCase() === 'video' ? (el.poster || '') : '';
                var key = src + '|' + el.duration + '|' + poster;
                if (reported.has(key)) return;
                if (reported.size >= 256) reported.clear();
                reported.add(key);
                var duration = isFinite(el.duration) ? el.duration : -1;
                bridge.reportVideoElementForDocument(token, src, duration, el.videoWidth || 0, el.videoHeight || 0,
                    location.href, poster, el.tagName.toLowerCase() === 'audio');
            }

            function watch(el) {
                report(el);
                if (watched.has(el)) return;
                watched.add(el);
                el.addEventListener('loadedmetadata', function() { report(el); });
                el.addEventListener('durationchange', function() { report(el); });
                el.addEventListener('loadeddata', function() { report(el); });
                function activity() {
                    if (!el.paused && !el.ended && (playing.has(el) || playing.size < 32)) playing.add(el);
                    else playing.delete(el);
                    bridge.reportMediaActivityForDocument(token, playing.size > 0);
                }
                el.addEventListener('play', activity);
                el.addEventListener('pause', activity);
                el.addEventListener('ended', activity);
                activity();
            }

            function scan(root) {
                root.querySelectorAll('video, audio').forEach(watch);
            }

            function whenReady() {
                token = bridge.documentToken();
                scan(document);
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(m) {
                        m.addedNodes.forEach(function(node) {
                            if (!(node instanceof Element)) return;
                            if (node.matches && node.matches('video, audio')) watch(node);
                            if (node.querySelectorAll) scan(node);
                        });
                    });
                    playing.forEach(function(el) { if (!el.isConnected || el.paused || el.ended) playing.delete(el); });
                    bridge.reportMediaActivityForDocument(token, playing.size > 0);
                });
                observer.observe(document.documentElement || document, { childList: true, subtree: true });
            }

            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', whenReady);
            } else {
                whenReady();
            }
        } catch (e) {}
    })();
"""

/**
 * Installs the video/audio-element sniffer via androidx.webkit's addDocumentStartJavaScript.
 * Requires a GrabberBridge already registered as "MiowserGrabber" on this WebView, and must be
 * called before loadUrl() for the first navigation to pick it up — same contract as
 * installCosmeticInjectionScript.
 */
fun WebView.installVideoSnifferScript() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, VIDEO_SNIFFER_SCRIPT, setOf("*"))
    } else {
        com.miowser.app.diagnostics.SafeLog.warning(TAG, "DOCUMENT_START_SCRIPT not supported — video-element sniffing degraded")
    }
}
