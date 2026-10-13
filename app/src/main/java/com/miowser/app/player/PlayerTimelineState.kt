package com.miowser.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player

internal data class PlayerTimelineSample(
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long
)

/** Only timeline/timer leaves read these states. Metadata and action rows never read a tick. */
internal class PlayerTimelineState(player: Player) {
    var sample by mutableStateOf(read(player))
        private set
    var scrubPositionMs by mutableStateOf<Long?>(null)

    fun refresh(player: Player) { sample = read(player) }

    private fun read(player: Player) = PlayerTimelineSample(
        player.currentPosition.coerceAtLeast(0L),
        player.bufferedPosition.coerceAtLeast(0L),
        player.knownDurationMs()
    )
}

@Composable
internal fun rememberPlayerTimeline(player: Player, visible: Boolean,
    onScrubbingCancelled: () -> Unit): PlayerTimelineState {
    val state = remember(player) { PlayerTimelineState(player) }
    val cancelScrubbing by rememberUpdatedState(onScrubbingCancelled)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player, visible, lifecycle) {
        if (!visible) {
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                observePlayerClock(player, intervalMs = 500L, sampleWhileBuffering = true) {
                    state.refresh(player)
                }
            } finally {
                if (state.scrubPositionMs != null) {
                    state.scrubPositionMs = null
                    cancelScrubbing()
                }
            }
        }
    }
    return state
}
