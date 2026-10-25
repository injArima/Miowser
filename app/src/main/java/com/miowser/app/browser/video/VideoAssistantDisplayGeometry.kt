package com.miowser.app.browser.video

/** Runtime-only normalized coordinates for a main-frame WebView visual viewport. */
internal data class VideoAssistantDisplayGeometry(
    val version: Long,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val viewportWidth: Double,
    val viewportHeight: Double
) {
    fun fits(viewWidth: Int, viewHeight: Int): Boolean {
        if (version !in 1..9_007_199_254_740_991L || viewWidth <= 0 || viewHeight <= 0 ||
            listOf(left, top, width, height, viewportWidth, viewportHeight).any { !it.isFinite() } ||
            left < 0 || top < 0 || width <= 0 || height <= 0 || left + width > 1.001 || top + height > 1.001 ||
            viewportWidth !in 1.0..32768.0 || viewportHeight !in 1.0..32768.0) return false
        val scaleX = viewWidth / viewportWidth
        val scaleY = viewHeight / viewportHeight
        val hitWidth = width * viewWidth
        val hitHeight = height * viewHeight
        return kotlin.math.abs(scaleX / scaleY - 1) <= 0.04 &&
            kotlin.math.abs(hitWidth / hitHeight - 1) <= 0.04 &&
            hitWidth in 16.0..384.0 && hitHeight in 16.0..384.0
    }
}
