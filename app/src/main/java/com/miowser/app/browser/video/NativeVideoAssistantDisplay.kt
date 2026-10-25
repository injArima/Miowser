package com.miowser.app.browser.video

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.miowser.app.R
import kotlin.math.roundToInt

/** Decorative native prototype. Every real touch passes through to the existing DOM button. */
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
        expiry?.let(::removeCallbacks)
        face.animate().cancel()
        val wasVisible = face.visibility == View.VISIBLE && face.alpha > 0f
        val hitWidth = geometry.width * width
        val hitHeight = geometry.height * height
        val size = (hitWidth * 40 / 48).roundToInt()
        face.layoutParams = LayoutParams(size, size)
        face.x = (geometry.left * width + (hitWidth - size) / 2).toFloat()
        face.y = (geometry.top * height + (hitHeight - size) / 2).toFloat()
        val padding = (size * 8.0 / 40).roundToInt()
        face.setPadding(padding, padding, padding, padding)
        face.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(background) }
        face.imageTintList = ColorStateList.valueOf(foreground)
        face.visibility = View.VISIBLE
        face.alpha = if (wasVisible || reducedMotion) 1f else 0f
        if (!wasVisible && !reducedMotion) face.animate().alpha(1f).setDuration(300).start()
        // Wait for the native layout/draw before suppressing the DOM's own visual face.
        postOnAnimation { postOnAnimation {
            if (generation == ticket) ready(isAttachedToWindow && isShown && face.isShown && face.width > 0 && face.height > 0)
        } }
        expiry = Runnable { if (generation == ticket) { clear(); onLost?.invoke() } }
            .also { postDelayed(it, 3600) }
    }

    fun dismiss(animate: Boolean) {
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
    override fun onAttachedToWindow() { super.onAttachedToWindow(); onLost?.invoke() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) { clear(); onLost?.invoke() }
    }
    override fun onDetachedFromWindow() {
        clear(); onLost?.invoke(); onLost = null
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
