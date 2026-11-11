package com.miowser.app.browser

import kotlin.math.abs

/** A refresh may claim only a clearly downward, one-finger gesture on ordinary page content. */
internal class BrowserPageGesturePolicy(private val touchSlop: Float) {
    private var pageAllowed = false
    private var pageOwnsGesture = true
    private var downward = false

    val canRefresh: Boolean get() = pageAllowed && !pageOwnsGesture && downward
    val needsPageCheck: Boolean get() = !pageOwnsGesture

    fun begin(atTop: Boolean) {
        pageAllowed = false
        pageOwnsGesture = !atTop
        downward = false
    }

    fun resolvePageTarget(allowRefresh: Boolean) {
        pageAllowed = allowRefresh
        if (!allowRefresh) claimForPage()
    }

    fun move(dx: Float, dy: Float, pointerCount: Int) {
        if (pointerCount != 1 || dy < -touchSlop || abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
            claimForPage()
        }
        downward = dy > touchSlop * 2f && dy > abs(dx) * 1.73f
    }

    fun claimForPage() { pageOwnsGesture = true }
    fun cancel() { pageAllowed = false; pageOwnsGesture = true; downward = false }
}
