package com.miowser.app.grabber

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class GrabberBridge(private val onActivity: (Boolean) -> Unit = {},
    private val onDetected: (String, Double, Int, Int, String, String, Boolean) -> Unit) {
    private data class Report(val src: String, val duration: Double, val width: Int,
        val height: Int, val frameUrl: String, val posterUrl: String, val audio: Boolean)
    private val main = Handler(Looper.getMainLooper())
    private val pending = linkedMapOf<String, Report>()
    private var activity: Boolean? = null
    private var scheduled = false
    private var generation = 0L
    private var retired = false
    fun reset() = synchronized(pending) { generation++; pending.clear(); activity = null }
    fun retire() = synchronized(pending) { retired = true; pending.clear(); activity = null }
    @JavascriptInterface fun documentToken(): String = synchronized(pending) { generation.toString() }

    @JavascriptInterface fun reportVideoElementForDocument(token: String, src: String, duration: Double,
        width: Int, height: Int, frameUrl: String, posterUrl: String, isAudio: Boolean) {
        if (token.length > 20 || src.isBlank() || src.length > 8192 || frameUrl.length > 8192 ||
            posterUrl.length > 8192) return
        synchronized(pending) {
            if (retired || token != generation.toString() || (src !in pending && pending.size >= 64)) return
            pending[src] = Report(src, duration.takeIf { it.isFinite() } ?: -1.0,
                width.coerceIn(0, 32768), height.coerceIn(0, 32768), frameUrl, posterUrl, isAudio)
            scheduleLocked()
        }
    }
    @JavascriptInterface fun reportMediaActivityForDocument(token: String, playing: Boolean) = synchronized(pending) {
        if (retired || token.length > 20 || token != generation.toString()) return@synchronized
        activity = playing
        scheduleLocked()
    }
    private fun scheduleLocked() {
        if (scheduled) return
        scheduled = true
        main.postDelayed({
            val batch = synchronized(pending) {
                val result = Triple(pending.values.toList(), activity, generation)
                pending.clear(); activity = null; scheduled = false
                result
            }
            synchronized(pending) {
                if (retired || generation != batch.third) return@postDelayed
            }
            batch.second?.let(onActivity)
            batch.first.forEach {
                onDetected(it.src, it.duration, it.width, it.height, it.frameUrl, it.posterUrl, it.audio)
            }
        }, 100)
    }
}
