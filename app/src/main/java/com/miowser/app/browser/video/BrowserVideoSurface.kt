package com.miowser.app.browser.video

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun suppressBrowserSurfaceInput(state: BrowserVideoPlaybackState): Boolean =
    state.session != null && state.phase in setOf(BrowserVideoPhase.ACTIVE,
        BrowserVideoPhase.SUSPENDED, BrowserVideoPhase.RECONNECTING)

/** The video keeps rendering. Only the underlying browser's pointer/accessibility UI is gated. */
@Composable
internal fun BrowserVideoSurface(view: View, controller: BrowserPlaybackControls?) {
    val policy = remember(controller) {
        controller?.state?.map(::suppressBrowserSurfaceInput)?.distinctUntilChanged() ?: flowOf(false)
    }
    val blocked by policy.collectAsState(controller?.state?.value?.let(::suppressBrowserSurfaceInput) ?: false)
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        BrowserVideoSurfaceHost(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }, update = { host -> host.blockBrowserInput = blocked }, onRelease = { it.removeAllViews() })
}

private class BrowserVideoSurfaceHost(context: Context) : FrameLayout(context) {
    var blockBrowserInput = false
        set(value) {
            if (field == value) return
            field = value
            importantForAccessibility = if (value) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            descendantFocusability = if (value) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = blockBrowserInput || super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean = blockBrowserInput || super.onTouchEvent(event)
}
