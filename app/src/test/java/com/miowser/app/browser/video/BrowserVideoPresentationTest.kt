package com.miowser.app.browser.video

import com.miowser.app.player.PlayerScaleMode
import org.junit.Assert.*
import org.junit.Test

class BrowserVideoPresentationTest {
    private fun state(): BrowserVideoPlaybackState {
        val video = LiveVideoState("video", "blob:https://test/video", LiveVideoSourceKind.BLOB,
            12_000, 45_000, true, false, false, false, 1.0, 1280, 720, true, 1.0,
            VideoFrameBounds(0.0, 0.0, 640.0, 360.0, 1080.0, 1920.0),
            listOf(VideoTimeRange(0, 45_000)), listOf(VideoTimeRange(0, 20_000)), 4, 1, false)
        return BrowserVideoPlaybackState(LiveVideoSession(LiveVideoId(1, 2, 3, "doc", "video"),
            "https://test", false, video, 1, 0), 4, phase = BrowserVideoPhase.ACTIVE)
    }

    @Test fun clockBufferGeometryAndDvrRangeUpdatesDoNotInvalidateControlPresentation() {
        val initial = state(); val expected = browserVideoPresentation(initial)
        repeat(1000) { tick ->
            val session = initial.session!!
            val next = initial.copy(session = session.copy(updatedAtMs = tick.toLong(),
                state = session.state.copy(positionMs = tick * 100L,
                    buffered = listOf(VideoTimeRange(0, 25_000 + tick.toLong())),
                    seekable = listOf(VideoTimeRange(tick.toLong(), 45_000 + tick.toLong())),
                    visible = false, intersectionRatio = 0.0)))
            assertEquals(expected, browserVideoPresentation(next))
        }
    }

    @Test fun supportedControlsAndRealPlaybackChangesStillReachTheUi() {
        val initial = state(); val session = initial.session!!; val video = session.state
        val variants = listOf(video.copy(playing = false), video.copy(buffering = true), video.copy(muted = true),
            video.copy(speed = 1.5), video.copy(videoHeight = 1080), video.copy(sourceRevision = 2),
            video.copy(captions = listOf(BrowserMediaTrack(1, "English", "en", true))),
            video.copy(audioTracks = listOf(BrowserMediaTrack(2, "Original", "ja", true))),
            video.copy(scale = BrowserVideoScale.ZOOM), video.copy(durationMs = null, seekable = emptyList()))
        variants.forEach { assertNotEquals(browserVideoPresentation(initial), browserVideoPresentation(initial.copy(session = session.copy(state = it)))) }
    }

    @Test fun pendingSeekClockDoesNotRecomposeTheControlsForEveryPrediction() {
        val initial = state()
        assertNotEquals(browserVideoPresentation(initial), browserVideoPresentation(initial.copy(pendingSeekMs = 20_000)))
        assertEquals(browserVideoPresentation(initial.copy(pendingSeekMs = 20_000)), browserVideoPresentation(initial.copy(pendingSeekMs = 25_000)))
        val reconnecting = browserVideoPresentation(initial.copy(phase = BrowserVideoPhase.RECONNECTING))
        assertFalse(reconnecting.controlsEnabled)
        assertNotEquals(browserVideoPresentation(initial), reconnecting)
        assertEquals("Failed", browserVideoPresentation(initial.copy(message = "Failed")).message)
    }

    @Test fun scrubPreviewIsSharedWithTheTimerAndClearedAfterRelease() {
        val interaction = BrowserVideoTimelineInteraction(); val initial = state()
        interaction.update(initial, 20_000); interaction.update(initial, 25_000)
        assertEquals(25_000L, interaction.scrubPositionMs); assertEquals(25_000L, interaction.targetFor(initial))
        interaction.clear(); assertNull(interaction.scrubPositionMs); assertNull(interaction.targetFor(initial))
    }

    @Test fun scrubReleaseCannotSeekAChangedSourceDifferentEntryOrSuspendedSession() {
        val interaction = BrowserVideoTimelineInteraction(); val initial = state()
        interaction.update(initial, 20_000)
        assertNull(interaction.targetFor(initial.copy(attempt = 99)))
        assertNull(interaction.targetFor(initial.copy(phase = BrowserVideoPhase.SUSPENDED)))
        assertNull(interaction.targetFor(initial.copy(session = initial.session!!.let { it.copy(state = it.state.copy(sourceRevision = 2)) })))
        assertNull(interaction.targetFor(initial.copy(session = initial.session!!.let { it.copy(id = it.id.copy(navigation = 99)) })))
        interaction.clear(); interaction.update(initial.copy(phase = BrowserVideoPhase.RECONNECTING), 30_000)
        assertNull(interaction.scrubPositionMs)
    }

    @Test fun browserScalingAndOriginalUiModesRetainTheSameOptions() {
        assertEquals(PlayerScaleMode.entries.map { it.name }, BrowserVideoScale.entries.map { it.name })
        assertEquals(PlayerScaleMode.entries.map { it.label }, BrowserVideoScale.entries.map { it.label })
    }
}
