package com.miowser.app.browser

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Native WebView drag recognition with feedback scoped to a live pull or committed refresh. */
internal data class BrowserPullFeedback(val progress: Float, val refreshing: Boolean)

internal class BrowserPullToRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    var onFeedbackChanged: (BrowserPullFeedback?) -> Unit = {}
    private val nativeSpinner = getChildAt(0).apply { alpha = 0f }
    private var gestureActive = false
    private var feedback: BrowserPullFeedback? = null
    private val pullObserver = ViewTreeObserver.OnPreDrawListener {
        publishFeedback()
        true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureActive = true
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> gestureActive = false
                MotionEvent.ACTION_CANCEL -> cancelPull()
            }
            publishFeedback()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A late UP after focus/tab ownership changed must not commit the abandoned pull.
        return if (!gestureActive && !isRefreshing) false else super.onTouchEvent(event)
    }

    fun cancelPull() {
        val wasPulling = gestureActive || nativeSpinner.visibility == View.VISIBLE
        gestureActive = false
        if (!isRefreshing && wasPulling) {
            // Also reset a native return animation interrupted by another touch owner.
            val enabled = isEnabled
            isEnabled = false
            isEnabled = enabled
        }
        publishFeedback()
    }

    fun finishRefresh() {
        isRefreshing = false
        gestureActive = false
        publishFeedback()
    }

    private fun publishFeedback() {
        val visible = isAttachedToWindow && visibility == View.VISIBLE && isEnabled && (isRefreshing ||
            gestureActive && nativeSpinner.visibility == View.VISIBLE &&
                nativeSpinner.top > progressViewStartOffset)
        val next = if (visible) BrowserPullFeedback(
            progress = if (isRefreshing) 1f else ((nativeSpinner.top - progressViewStartOffset).toFloat() /
                progressViewEndOffset.coerceAtLeast(1)).coerceIn(0f, 1f), refreshing = isRefreshing
        ) else null
        if (feedback != next) {
            feedback = next
            onFeedbackChanged(next)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelPull()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(pullObserver)
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(pullObserver)
        gestureActive = false
        super.onDetachedFromWindow()
        feedback = null
        onFeedbackChanged(null)
    }
}
