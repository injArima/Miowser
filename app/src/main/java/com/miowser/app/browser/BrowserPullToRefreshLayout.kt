package com.miowser.app.browser

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Native WebView drag recognition with feedback scoped to a live pull or committed refresh. */
internal data class BrowserPullFeedback(val progress: Float, val refreshing: Boolean)

internal class BrowserPullToRefreshLayout(context: Context) : SwipeRefreshLayout(context) {
    var onFeedbackChanged: (BrowserPullFeedback?) -> Unit = {}
    var checkPageTarget: ((Float, Float, (Boolean) -> Unit) -> Unit)? = null
    private val nativeSpinner = getChildAt(0).apply { alpha = 0f }
    private val pageGesture = BrowserPageGesturePolicy(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private var gestureActive = false
    private var intercepting = false
    private var gestureGeneration = 0L
    private var downX = 0f
    private var downY = 0f
    private var feedback: BrowserPullFeedback? = null
    private val pullObserver = ViewTreeObserver.OnPreDrawListener {
        publishFeedback()
        true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureGeneration++
            gestureActive = true
            intercepting = false
            downX = event.x; downY = event.y
            pageGesture.begin(isEnabled && !isRefreshing && !canChildScrollUp())
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            pageGesture.move(event.x - downX, event.y - downY, event.pointerCount)
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            pageGesture.claimForPage()
            if (intercepting) cancelPull()
        }
        return try {
            val handled = super.dispatchTouchEvent(event)
            // Dispatch DOWN first: page clicks never wait for a JS/native round trip.
            if (event.actionMasked == MotionEvent.ACTION_DOWN && pageGesture.needsPageCheck) {
                val ticket = gestureGeneration
                val check = checkPageTarget
                if (check == null || width <= 0 || height <= 0) pageGesture.resolvePageTarget(false)
                else check(event.x / width, event.y / height) { allowed ->
                    if (ticket == gestureGeneration && gestureActive) pageGesture.resolvePageTarget(allowed)
                }
            }
            handled
        } finally {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    gestureActive = false
                    intercepting = false
                    pageGesture.cancel()
                }
                MotionEvent.ACTION_CANCEL -> cancelPull()
            }
            publishFeedback()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !intercepting && !pageGesture.canRefresh) return false
        return super.onInterceptTouchEvent(event).also { if (it) intercepting = true }
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // SwipeRefreshLayout's legacy nested-scroll check can ignore WebView's request.
        if (disallowIntercept) pageGesture.claimForPage()
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A late UP after focus/tab ownership changed must not commit the abandoned pull.
        return if (!gestureActive && !isRefreshing) false else super.onTouchEvent(event)
    }

    fun cancelPull() {
        val wasPulling = gestureActive || nativeSpinner.visibility == View.VISIBLE
        gestureGeneration++
        gestureActive = false
        intercepting = false
        pageGesture.cancel()
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
        intercepting = false
        gestureGeneration++
        pageGesture.cancel()
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
        gestureGeneration++
        pageGesture.cancel()
        super.onDetachedFromWindow()
        feedback = null
        onFeedbackChanged(null)
    }
}
