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
        c.playPause(); c.pause(); c.skip(10_000); c.seekTo(5000); c.setSpeed(1.5); c.setRichUi(true)
        c.setMuted(true); c.setScale(BrowserVideoScale.ZOOM); c.selectCaption(null); c.selectAudio(1)
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

    @Test fun trackCommandsOnlyAcceptIdsExposedByTheCurrentOwner() {
        val sample = session().let { it.copy(state = it.state.copy(
            captions = listOf(BrowserMediaTrack(17, "English", "en", false)),
            audioTracks = listOf(BrowserMediaTrack(25, "Original", "ja", true)))) }
        val c = controller(sample)
        c.selectCaption(999); c.selectAudio(999)
        assertTrue(sent.isEmpty())
        c.selectCaption(17); c.selectCaption(null); c.selectAudio(25)
        assertEquals(listOf(17.0, -1.0, 25.0), sent.map { it.value })
        assertTrue(sent.all { it.owner == sample.id && it.attempt == 3L })
    }

    @Test fun muteAndScaleCommandsDoNotChangeTheSampledSourceClockOrPlayState() {
        val c = controller(); val original = c.state.value.session
        c.setMuted(true); c.setMuted(false); c.setScale(BrowserVideoScale.ZOOM); c.setScale(BrowserVideoScale.FILL)
        assertEquals(listOf(1.0, 0.0, 1.0, 2.0), sent.map { it.value })
        assertEquals(original, c.state.value.session)
        assertEquals(4, sent.size)
    }

    @Test fun explicitPauseNeverTogglesToPlayDuringPipDismissal() {
        val c = controller()
        c.pause(); c.pause()
        assertEquals(listOf(BrowserVideoAction.PAUSE, BrowserVideoAction.PAUSE), sent.map { it.action })
    }

    @Test fun speedHoldRestoresPriorSpeedOnceOnReleaseOrCancellation() {
        val c = controller(session().let { it.copy(state = it.state.copy(speed = 1.5)) })
        val hold = BrowserVideoSpeedHold()
        assertTrue(hold.start(c)); assertFalse(hold.start(c))
        hold.stop(c); hold.stop(c)
        assertEquals(listOf(BrowserVideoAction.HOLD_RATE, BrowserVideoAction.RELEASE_RATE), sent.map { it.action })
    }

    @Test fun speedHoldNeverRestoresOntoAnotherAttemptOrVideo() {
        val c = controller(); val hold = BrowserVideoSpeedHold()
        hold.start(c); c.attach(session(), 4); hold.stop(c)
        assertEquals(listOf(BrowserVideoAction.HOLD_RATE), sent.map { it.action })
        hold.start(c); c.attach(session().copy(id = session().id.copy(elementId = "other")), 4); hold.stop(c)
        assertEquals(listOf(BrowserVideoAction.HOLD_RATE, BrowserVideoAction.HOLD_RATE), sent.map { it.action })
    }

    @Test fun speedHoldDoesNotStartWhilePausedAndLateReleaseAfterExitIsHarmless() {
        val c = controller(session().let { it.copy(state = it.state.copy(playing = false)) })
        val hold = BrowserVideoSpeedHold()
        assertFalse(hold.start(c)); assertTrue(sent.isEmpty())
        c.sample(session()); assertTrue(hold.start(c)); c.detach(); hold.stop(c)
        assertEquals(listOf(BrowserVideoAction.HOLD_RATE), sent.map { it.action })
    }

    @Test fun pipRendererRequiresMatchingTabAcceptedFullscreenNavigationElementAndAttempt() {
        val state = controller().state.value
        val owner = BrowserVideoPipOwner(state.session!!.id, state.attempt)
        assertTrue(keepBrowserRendererForPip(owner, 1, 1, state))
        assertFalse(keepBrowserRendererForPip(null, 1, 1, state))
        assertFalse(keepBrowserRendererForPip(owner, null, 1, state))
        assertFalse(keepBrowserRendererForPip(owner, 2, 1, state))
        assertFalse(keepBrowserRendererForPip(owner, 1, 2, state))
        assertFalse(keepBrowserRendererForPip(owner, 1, 1, state.copy(attempt = 99)))
        assertFalse(keepBrowserRendererForPip(owner, 1, 1, state.copy(session = state.session.copy(id = owner.id.copy(navigation = 99)))))
        assertFalse(keepBrowserRendererForPip(owner, 1, 1, state.copy(session = state.session.copy(id = owner.id.copy(elementId = "other")))))
        assertFalse(keepBrowserRendererForPip(owner, 1, 1, BrowserVideoPlaybackState()))
    }

    private fun session(): LiveVideoSession = LiveVideoSession(LiveVideoId(1, 1, 2, "document", "video"),
        "https://test", false, LiveVideoState("video", "blob:https://test/video", LiveVideoSourceKind.BLOB,
            12_000, 45_000, true, false, false, false, 1.0, 1280, 720, true, 1.0,
            VideoFrameBounds(0.0, 0.0, 640.0, 360.0, 1080.0, 1920.0),
            listOf(VideoTimeRange(0, 45_000)), listOf(VideoTimeRange(0, 20_000)), 4, 1, false), 1, 0)
}
