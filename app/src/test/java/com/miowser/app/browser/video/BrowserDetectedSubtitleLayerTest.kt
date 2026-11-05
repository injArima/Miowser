package com.miowser.app.browser.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserDetectedSubtitleLayerTest {
    private val id = LiveVideoId(1, 2, 3, "document", "video")
    private val owner = BrowserVideoPipOwner(id, 4)

    private fun state(playing: Boolean = true, buffering: Boolean = false): BrowserVideoPlaybackState {
        val video = LiveVideoState("video", 10_000, 60_000, playing, buffering, false, 1.5,
            1280, 720, emptyList(), emptyList(), 4, 1)
        return BrowserVideoPlaybackState(LiveVideoSession(id, video, 1_000), 4,
            phase = BrowserVideoPhase.ACTIVE)
    }

    @Test fun captionsFollowTheOwnedWebViewClockWithoutAdvancingWhilePausedOrBuffering() {
        assertEquals(10_750L, browserSubtitlePositionMs(state(), owner, 1_500))
        assertEquals(10_000L, browserSubtitlePositionMs(state(playing = false), owner, 1_500))
        assertEquals(10_000L, browserSubtitlePositionMs(state(buffering = true), owner, 1_500))
        assertEquals(11_500L, browserSubtitlePositionMs(state(), owner, 10_000))
    }

    @Test fun staleOrDifferentPlaybackCannotShowCaptions() {
        assertNull(browserSubtitlePositionMs(state(), owner.copy(attempt = 5), 1_500))
        assertNull(browserSubtitlePositionMs(state().copy(phase = BrowserVideoPhase.RECONNECTING), owner, 1_500))
        assertEquals(20_000L, browserSubtitlePositionMs(state().copy(pendingSeekMs = 20_000), owner, 1_500))
    }
}
