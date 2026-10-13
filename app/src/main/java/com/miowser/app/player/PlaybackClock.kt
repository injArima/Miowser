package com.miowser.app.player

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal fun Player.knownDurationMs(): Long =
    duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

/** A paused clock sleeps until an event. Cancellation releases its owner immediately. */
internal suspend fun observePlaybackClock(
    intervalMs: Long,
    changes: ReceiveChannel<Unit>,
    isAdvancing: () -> Boolean,
    update: () -> Unit
) {
    require(intervalMs > 0L)
    while (currentCoroutineContext().isActive) {
        update()
        if (isAdvancing()) withTimeoutOrNull(intervalMs) { changes.receive() }
        else changes.receive()
    }
}

/** Called only inside the visible component's STARTED lifecycle scope, on the player thread. */
internal suspend fun observePlayerClock(
    player: Player,
    intervalMs: Long,
    sampleWhileBuffering: Boolean = false,
    update: () -> Unit
) {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            changes.trySend(Unit)
        }
    }
    player.addListener(listener)
    try {
        observePlaybackClock(intervalMs, changes,
            // A paused visible timeline still shows bytes arriving while Media3 is loading.
            // Once paused and idle, both it and the subtitle renderer sleep until an event.
            isAdvancing = { player.isPlaying || (sampleWhileBuffering && player.isLoading) },
            update = update)
    } finally {
        player.removeListener(listener)
        changes.close()
    }
}
