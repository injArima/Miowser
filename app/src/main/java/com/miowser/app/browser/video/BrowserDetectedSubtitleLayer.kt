package com.miowser.app.browser.video

import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.ui.SubtitleView
import com.miowser.app.player.PlayerSubtitleStyle
import com.miowser.app.player.applyPlayerSubtitleStyle
import com.miowser.app.player.externalWebvttCuesAt
import com.miowser.app.player.positionSubtitleCues
import com.miowser.app.player.vttPositionMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest

/** Observe only while Inspect is visible, and sleep when the owned video is paused. */
@Composable
internal fun rememberBrowserSubtitlePlaybackPosition(
    controller: BrowserPlaybackControls,
    owner: BrowserVideoPipOwner
): State<Long?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<Long?>(
        browserSubtitlePositionMs(controller.state.value, owner, SystemClock.elapsedRealtime()),
        controller, owner, lifecycle
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            controller.state.collectLatest { state ->
                do {
                    value = browserSubtitlePositionMs(state, owner, SystemClock.elapsedRealtime())
                    val video = state.session?.state
                    if (value == null || video?.playing != true || video.buffering) break
                    delay(100)
                } while (isActive)
            }
        }
    }
}

/** Uses the owned WebView video's clock; no second decoder or stream is opened. */
internal fun browserSubtitlePositionMs(
    state: BrowserVideoPlaybackState,
    owner: BrowserVideoPipOwner,
    nowMs: Long
): Long? {
    val session = state.session ?: return null
    if (session.id != owner.id || state.attempt != owner.attempt ||
        state.phase != BrowserVideoPhase.ACTIVE) return null
    state.pendingSeekMs?.let { return it }
    val video = session.state
    val elapsed = if (video.playing && !video.buffering) {
        (nowMs - session.updatedAtMs).coerceIn(0L, 1_000L)
    } else 0L
    return (video.positionMs + (elapsed * video.speed).toLong()).coerceAtLeast(0L)
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun BrowserDetectedSubtitleLayer(
    controller: BrowserPlaybackControls,
    owner: BrowserVideoPipOwner,
    selectedUrl: String?,
    cues: List<CuesWithTiming>,
    style: PlayerSubtitleStyle,
    typeface: Typeface?
) {
    var view by remember { mutableStateOf<SubtitleView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> SubtitleView(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            applyPlayerSubtitleStyle(this, style, typeface)
            view = this
        } },
        update = { subtitleView ->
            applyPlayerSubtitleStyle(subtitleView, style, typeface)
            if (view !== subtitleView) view = subtitleView
        },
        onRelease = { subtitleView ->
            subtitleView.setCues(emptyList())
            if (view === subtitleView) view = null
        }
    )
    LaunchedEffect(controller, owner, selectedUrl, cues, style, view, lifecycle) {
        val subtitleView = view ?: return@LaunchedEffect
        subtitleView.setCues(emptyList())
        if (selectedUrl == null || cues.isEmpty()) return@LaunchedEffect
        var last = emptyList<androidx.media3.common.text.Cue>()
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val position = browserSubtitlePositionMs(controller.state.value, owner,
                        SystemClock.elapsedRealtime())
                    val next = position?.let {
                        positionSubtitleCues(externalWebvttCuesAt(cues, style.vttPositionMs(it)),
                            style.bottomPadding)
                    }.orEmpty()
                    if (next != last) {
                        subtitleView.setCues(next)
                        last = next
                    }
                    delay(80)
                }
            }
        } finally { subtitleView.setCues(emptyList()) }
    }
}
