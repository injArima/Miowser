package com.miowser.app.browser.video

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import com.miowser.app.R
import kotlin.math.roundToInt

/** Native face and gesture owner; only an acknowledged physical tap enters the WebView. */
internal class NativeVideoAssistantDisplay(context: Context) : FrameLayout(context) {
    private val face = ImageView(context).apply {
        setImageResource(R.drawable.ic_miowser_mark)
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        elevation = 3 * resources.displayMetrics.density
        visibility = View.INVISIBLE
    }
    private var generation = 0L
    private var expiry: Runnable? = null
    private var onLost: (() -> Unit)? = null
    private val hit = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var visibleVersion: Long? = null
    private var presentationReady = false
    private var capturing = false
    val ownsGesture: Boolean get() = capturing
    private var nextPress = 0L
    private var inputTarget: View? = null
    private var sendInput: ((String, Long, Long) -> Unit)? = null
    private data class Press(val version: Long, val request: Long, val down: MotionEvent,
        var up: MotionEvent? = null, var ready: Boolean = false, var requested: Boolean = false)
    private var press: Press? = null
    private var pressTimeout: Runnable? = null
    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(face, LayoutParams(1, 1))
    }

    fun present(geometry: VideoAssistantDisplayGeometry, background: Int, foreground: Int,
        reducedMotion: Boolean, ready: (Boolean) -> Unit) {
        if (!geometry.fits(width, height) || !isAttachedToWindow) { clear(); ready(false); return }
        val ticket = ++generation
        if (visibleVersion != geometry.version) cancelPress()
        visibleVersion = geometry.version
        presentationReady = false
        expiry?.let(::removeCallbacks)
        face.animate().cancel()
        val wasVisible = face.visibility == View.VISIBLE && face.alpha > 0f
        val hitWidth = geometry.width * width
        val hitHeight = geometry.height * height
        hit.set((geometry.left * width).toFloat(), (geometry.top * height).toFloat(),
            ((geometry.left + geometry.width) * width).toFloat(),
            ((geometry.top + geometry.height) * height).toFloat())
        val size = (hitWidth * 48 / 60).roundToInt()
        face.layoutParams = LayoutParams(size, size)
        face.x = (geometry.left * width + (hitWidth - size) / 2).toFloat()
        face.y = (geometry.top * height + (hitHeight - size) / 2).toFloat()
        val padding = (size * 10.0 / 48).roundToInt()
        face.setPadding(padding, padding, padding, padding)
        face.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(background) }
        face.imageTintList = ColorStateList.valueOf(foreground)
        face.visibility = View.VISIBLE
        face.alpha = if (wasVisible || reducedMotion) 1f else 0f
        if (!wasVisible && !reducedMotion) face.animate().alpha(1f).setDuration(300).start()
        // Wait for the native layout/draw before suppressing the DOM's own visual face.
        postOnAnimation { postOnAnimation {
            if (generation == ticket) {
                presentationReady = isAttachedToWindow && isShown && face.isShown && face.width > 0 && face.height > 0
                ready(presentationReady)
                if (presentationReady) preparePress() else cancelPress()
            }
        } }
        expiry = Runnable { if (generation == ticket) { clear(); onLost?.invoke() } }
            .also { postDelayed(it, 3600) }
    }

    fun dismiss(animate: Boolean) {
        cancelPress()
        visibleVersion = null
        presentationReady = false
        generation++
        expiry?.let(::removeCallbacks)
        expiry = null
        face.animate().cancel()
        if (animate && face.visibility == View.VISIBLE) {
            val ticket = generation
            face.animate().alpha(0f).setDuration(300).withEndAction {
                if (generation == ticket) face.visibility = View.INVISIBLE
            }.start()
        } else { face.alpha = 0f; face.visibility = View.INVISIBLE }
    }
    fun clear() = dismiss(false)
    fun setOnLost(listener: (() -> Unit)?) { onLost = listener }

    fun connectInput(target: View?, send: ((String, Long, Long) -> Unit)?) {
        cancelPress()
        inputTarget = target
        sendInput = send
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelPress()
            capturing = isShown && face.visibility == View.VISIBLE && hit.contains(event.x, event.y)
            if (!capturing) return false
            // Own the whole gesture, including a drag or a tap during fade-out. Neither
            // SwipeRefreshLayout nor the page underneath may turn it into a scroll/click.
            parent?.requestDisallowInterceptTouchEvent(true)
            val version = visibleVersion
            if (version != null && inputTarget != null && sendInput != null) {
                press = Press(version, ++nextPress, MotionEvent.obtainNoHistory(event))
                expiry?.let(::removeCallbacks)
                val ticket = generation
                expiry = Runnable { if (generation == ticket) { clear(); onLost?.invoke() } }
                    .also { postDelayed(it, 3600) }
                pressTimeout = Runnable { cancelPress() }.also { postDelayed(it, 700) }
                preparePress()
            }
            return true
        }
        if (!capturing) return false
        val current = press
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (current != null &&
                (kotlin.math.abs(event.x - current.down.x) > slop ||
                    kotlin.math.abs(event.y - current.down.y) > slop)) cancelPress()
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> cancelPress()
            MotionEvent.ACTION_UP -> {
                if (current != null) {
                    if (event.eventTime - current.down.eventTime > ViewConfiguration.getLongPressTimeout() ||
                        !hit.contains(event.x, event.y)) cancelPress()
                    else { current.up = MotionEvent.obtainNoHistory(event); forwardPress() }
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            capturing = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    fun acknowledgePress(version: Long, request: Long, ok: Boolean) {
        val current = press ?: return
        if (!current.requested || current.version != version || current.request != request) return
        if (!ok || visibleVersion != version) { cancelPress(); return }
        current.ready = true
        forwardPress()
    }

    private fun preparePress() {
        val current = press ?: return
        if (!presentationReady || current.requested) return
        current.requested = true
        // The display-ready reply is sent first, including when a very quick tap
        // began during the initial two-frame layout acknowledgement.
        sendInput?.invoke("nativePress", current.version, current.request)
    }

    private fun forwardPress() {
        val current = press ?: return
        val up = current.up ?: return
        val target = inputTarget ?: return
        if (!current.ready) return
        press = null
        pressTimeout?.let(::removeCallbacks); pressTimeout = null
        val here = IntArray(2); val there = IntArray(2)
        getLocationOnScreen(here); target.getLocationOnScreen(there)
        try {
            // These are copies of this gesture's real DOWN/UP, with original timing and
            // source. Chromium generates the trusted click; never call JavaScript click().
            if (target.isAttachedToWindow) for (event in listOf(current.down, up)) {
                event.offsetLocation((here[0] - there[0]).toFloat(), (here[1] - there[1]).toFloat())
                target.dispatchTouchEvent(event)
            }
        } finally {
            current.down.recycle(); up.recycle()
        }
    }

    private fun cancelPress() {
        pressTimeout?.let(::removeCallbacks); pressTimeout = null
        val current = press ?: return
        press = null
        current.down.recycle(); current.up?.recycle()
        sendInput?.invoke("nativePressCancel", current.version, current.request)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); onLost?.invoke() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) { clear(); onLost?.invoke() }
    }
    override fun onDetachedFromWindow() {
        capturing = false
        parent?.requestDisallowInterceptTouchEvent(false)
        clear(); onLost?.invoke(); onLost = null
        connectInput(null, null)
        super.onDetachedFromWindow()
    }
}

/** SwipeRefreshLayout also owns a spinner child; its first child is not our content. */
internal fun releaseVideoAssistantHost(host: ViewGroup, detach: (NativeVideoAssistantDisplay) -> Unit) {
    for (index in 0 until host.childCount) {
        val content = host.getChildAt(index) as? FrameLayout ?: continue
        for (child in 0 until content.childCount) {
            (content.getChildAt(child) as? NativeVideoAssistantDisplay)?.let(detach)
        }
        content.removeAllViews()
    }
    host.removeAllViews()
}
