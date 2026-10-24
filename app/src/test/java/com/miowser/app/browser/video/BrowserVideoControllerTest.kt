package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class BrowserVideoControllerTest {
    private val sent = mutableListOf<BrowserVideoCommand>()
    private fun controller(sample: LiveVideoSession? = session()): BrowserVideoController = BrowserVideoController().also { c ->
        c.connect { sent += it; true }
        sample?.let { c.attach(it, 3) }
    }

    @Test fun noCommandsWithoutAFullscreenOwner() {
        val c = controller(null)
        c.playPause(); c.skip(10_000); c.seekTo(5000); c.setSpeed(1.5); c.setRichUi(true)
        assertTrue(sent.isEmpty())
    }
    @Test fun rapidTransportTapsToggleExplicitIntentInsteadOfReusingAStaleClock() {
        val c = controller()
        c.playPause(); c.playPause()
        assertEquals(listOf(BrowserVideoAction.PAUSE, BrowserVideoAction.PLAY), sent.map { it.action })
        assertEquals(session().id, sent.first().owner)
        assertEquals(3L, sent.first().attempt)
    }
    @Test fun finiteDurationWithoutSeekableRangesStillAttemptsAConfirmedSeek() {
        val sample = session().let { it.copy(state = it.state.copy(seekable = emptyList())) }
        val c = controller(sample)
        assertTrue(c.state.value.canSeek)
        c.seekTo(25_000)
        assertEquals(25.0, sent.single().value!!, 0.0)
        assertEquals(25_000L, c.state.value.pendingSeekMs)
    }
    @Test fun unknownLiveWindowDoesNotInventSeekSupport() {
        val sample = session().let { it.copy(state = it.state.copy(durationMs = null, seekable = emptyList())) }
        val c = controller(sample)
        c.seekTo(25_000); c.skip(10_000)
        assertFalse(c.state.value.canSeek)
        assertTrue(sent.isEmpty())
    }
    @Test fun skipCommandsCarryDeltasForThePagesFreshPosition() {
        val c = controller()
        c.skip(10_000)
        assertEquals(BrowserVideoAction.SKIP, sent.single().action)
        assertEquals(10.0, sent.single().value!!, 0.0)
    }
    @Test fun holesAndLiveWindowBoundariesAreClamped() {
        val sample = session().let { it.copy(state = it.state.copy(durationMs = null,
            seekable = listOf(VideoTimeRange(5000, 10_000), VideoTimeRange(20_000, 30_000)))) }
        val c = controller(sample)
        c.seekTo(14_000); c.seekTo(100_000)
        assertEquals(listOf(10.0, 30.0), sent.map { it.value })
    }
    @Test fun onlyFiniteSupportedSpeedRequestsReachThePage() {
        val c = controller()
        c.setSpeed(Double.NaN); c.setSpeed(Double.POSITIVE_INFINITY); c.setSpeed(8.0); c.setSpeed(0.1)
        assertTrue(sent.isEmpty())
        c.setSpeed(1.5)
        assertEquals(1.5, sent.single().value!!, 0.0)
    }
    @Test fun failedSeekClearsThePendingTargetAndShowsRecovery() {
        val c = controller()
        c.seekTo(25_000); c.completed(BrowserVideoAction.SEEK, false)
        assertNull(c.state.value.pendingSeekMs)
        assertNotNull(c.state.value.message)
        assertEquals(12_000L, c.state.value.session?.state?.positionMs)
    }
    @Test fun oldSessionSamplesCannotReplaceTheNewOwner() {
        val c = controller()
        c.sample(session().copy(id = session().id.copy(navigation = 99)))
        assertEquals(1L, c.state.value.session?.id?.navigation)
    }
    @Test fun newFullscreenAttemptClearsOldSeekAndTransportIntent() {
        val c = controller()
        c.seekTo(25_000); c.playPause(); c.attach(session(), 4)
        assertNull(c.state.value.pendingSeekMs)
        c.playPause()
        assertEquals(BrowserVideoAction.PAUSE, sent.last().action)
        assertEquals(4L, sent.last().attempt)
    }
    @Test fun pageAcknowledgedPositionReplacesTheSampledSeekPrediction() {
        val c = controller()
        c.skip(10_000)
        c.sample(session().let { it.copy(state = it.state.copy(positionMs = 23_000)) })
        c.started(BrowserVideoAction.SKIP)
        assertEquals(23_000L, c.state.value.pendingSeekMs)
        c.completed(BrowserVideoAction.SKIP, true)
        assertNull(c.state.value.pendingSeekMs)
    }
    @Test fun disconnectDropsLateResultsAndStopsFurtherCommands() {
        val c = controller()
        c.disconnect(); c.playPause(); c.completed(BrowserVideoAction.PLAY, false)
        assertTrue(sent.isEmpty()); assertNull(c.state.value.session); assertNull(c.state.value.message)
    }
    @Test fun failedDeliveryDoesNotLeaveTheTimelinePendingForever() {
        val c = controller()
        c.connect { false }; c.seekTo(25_000)
        assertNull(c.state.value.pendingSeekMs); assertNotNull(c.state.value.message)
    }

    private fun session(): LiveVideoSession = LiveVideoSession(LiveVideoId(1, 1, 2, "document", "video"),
        "https://test", false, LiveVideoState("video", "blob:https://test/video", LiveVideoSourceKind.BLOB,
            12_000, 45_000, true, false, false, false, 1.0, 1280, 720, true, 1.0,
            VideoFrameBounds(0.0, 0.0, 640.0, 360.0, 1080.0, 1920.0),
            listOf(VideoTimeRange(0, 45_000)), listOf(VideoTimeRange(0, 20_000)), 4, 1, false), 1, 0)
}
