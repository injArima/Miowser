package com.miowser.app.player

import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import java.lang.reflect.Proxy
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PlaybackClockTest {
    @Test fun pausedClockSamplesOnceAndSleepsUntilSeek() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        val samples = Channel<Long>(Channel.UNLIMITED)
        var position = 1_000L
        val job = launch {
            observePlaybackClock(50, changes, { false }) { samples.trySend(position) }
        }
        try {
            assertEquals(1_000L, withTimeout(2_000) { samples.receive() })
            repeat(10) { yield() }
            assertNull(withTimeoutOrNull(120) { samples.receive() })
            position = 9_999L
            changes.send(Unit)
            assertEquals(9_999L, withTimeout(2_000) { samples.receive() })
        } finally { job.cancelAndJoin() }
    }

    @Test fun advancingClockTicksAndStopsOnPauseEvent() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        val samples = Channel<Unit>(Channel.UNLIMITED)
        var playing = true
        val job = launch { observePlaybackClock(1, changes, { playing }) { samples.trySend(Unit) } }
        try {
            withTimeout(2_000) { samples.receive(); samples.receive() }
            playing = false
            changes.send(Unit)
            // Let the pause event be consumed and the owner settle into its event wait.
            repeat(10) { yield() }
            while (samples.tryReceive().isSuccess) { /* drain already published ticks */ }
            repeat(10) { yield() }
            assertNull(withTimeoutOrNull(120) { samples.receive() })
        } finally { job.cancelAndJoin() }
    }

    @Test fun resumedOwnerRefreshesWithoutWaitingForATick() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        var position = 10L
        val samples = Channel<Long>(Channel.UNLIMITED)
        suspend fun startAndStop() {
            val job = launch { observePlaybackClock(50, changes, { false }) { samples.trySend(position) } }
            try { assertEquals(position, withTimeout(2_000) { samples.receive() }) }
            finally { job.cancelAndJoin() }
        }
        startAndStop()
        position = 8_000L // Playback can advance while this UI owner is absent.
        startAndStop()
        repeat(10) { yield() }
        assertTrue(samples.tryReceive().isFailure)
    }

    @Test fun cancellingPlayerClockRemovesListenerWithoutChangingPlayback() = runBlocking {
        var added: Player.Listener? = null
        var removed: Player.Listener? = null
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader,
            arrayOf(Player::class.java)) { _, method, args ->
            calls += method.name
            when (method.name) {
                "addListener" -> { added = args!![0] as Player.Listener; null }
                "removeListener" -> { removed = args!![0] as Player.Listener; null }
                "isPlaying", "getIsPlaying", "isLoading", "getIsLoading", "getPlayWhenReady" -> false
                else -> null
            }
        } as Player
        val samples = Channel<Unit>(Channel.UNLIMITED)
        val job = launch { observePlayerClock(player, 50, sampleWhileBuffering = true) { samples.trySend(Unit) } }
        withTimeout(2_000) { samples.receive() }
        job.cancelAndJoin()
        assertNotNull(added)
        assertSame(added, removed)
        assertFalse(calls.any { it in listOf("pause", "play", "stop", "release", "seekTo") })
    }

    @Test fun millisecondDelayAndPausedRestyleKeepTheSelectedCue() {
        val cue = Cue.Builder().setText("The quick brown fox").build()
        val timeline = listOf(CuesWithTiming(listOf(cue), 1_000_000, 1_000_000))
        val delayed = PlayerSubtitleStyle(delayMs = 1L, bottomPadding = 0.06f)
        val moved = delayed.copy(bottomPadding = 0.25f)
        assertTrue(externalWebvttCuesAt(timeline, delayed.vttPositionMs(1_000L)).isEmpty())
        val active = externalWebvttCuesAt(timeline, delayed.vttPositionMs(1_001L))
        assertEquals(listOf(cue), active)
        assertEquals(cue.text, positionSubtitleCues(active, moved.bottomPadding).single().text)
        assertEquals(0.75f, positionSubtitleCues(active, moved.bottomPadding).single().line, 0.001f)
        assertTrue(externalWebvttCuesAt(timeline, delayed.vttPositionMs(2_001L)).isEmpty())
    }
}
