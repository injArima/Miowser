package com.miowser.app.grabber

import android.webkit.JavascriptInterface

/**
 * Exposed to page JS as `window.MiowserGrabber` for the in-page video/audio-element sniffer
 * (see installVideoSnifferScript) to report what it finds. Complements network-level detection
 * — catches <video>/<audio> elements and blob: URLs (MSE-based players) that never show up as a
 * plain HTTP request Chromium hands to shouldInterceptRequest. Safe to expose to untrusted page
 * content: the only method just reports element attributes the page already controls anyway.
 */
class GrabberBridge(private val onDetected: (src: String, duration: Double, width: Int, height: Int) -> Unit) {
    @JavascriptInterface
    fun reportVideoElement(src: String, duration: Double, width: Int, height: Int) {
        if (src.isBlank()) return
        onDetected(src, duration, width, height)
    }
}
