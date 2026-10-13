package com.miowser.app.player

import android.graphics.Typeface
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.ui.SubtitleView

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ExternalSubtitleLayer(player: Player, view: SubtitleView?, selectedUrl: String?,
    timeline: List<CuesWithTiming>, style: PlayerSubtitleStyle, typeface: Typeface?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player, view, selectedUrl, timeline, style, typeface, lifecycle) {
        if (view == null) return@LaunchedEffect
        view.visibility = if (selectedUrl == null) View.GONE else View.VISIBLE
        applyPlayerSubtitleStyle(view, style, typeface)
        if (selectedUrl == null) { view.setCues(emptyList()); return@LaunchedEffect }
        var last: List<Cue>? = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            observePlayerClock(player, intervalMs = 50L) {
                val cues = externalWebvttCuesAt(timeline, style.vttPositionMs(player.currentPosition))
                if (last != cues) {
                    view.setCues(positionSubtitleCues(cues, style.bottomPadding))
                    last = cues
                }
            }
        }
    }
}
