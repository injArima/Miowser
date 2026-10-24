package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class LiveVideoSessionsTest {
    @Test fun aLongBackgroundOrPipSessionProtectsOnlyItsExactOwnerFromLeaseExpiry() {
        val store = LiveVideoSessionRegistry(7)
        val frame = store.openFrame("doc", "https://test", true)!!
        val otherFrame = store.openFrame("other", "https://other.test", false)!!
        store.update(0, frame, 0, listOf(video(), video().copy(elementId = "sibling")), emptyList(), 1)
        store.update(0, otherFrame, 0, listOf(video()), emptyList(), 1)
        val owner = store.snapshot().first().id
        store.prune(600_000, owner)
        assertEquals(listOf(owner), store.snapshot().map { it.id })
        store.prune(600_001)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun protectionDoesNotResurrectRemovedElementsOrSurviveDocumentNavigation() {
        val store = LiveVideoSessionRegistry(7)
        val frame = store.openFrame("doc", "https://test", true)!!
        store.update(0, frame, 0, listOf(video()), emptyList(), 1)
        val owner = store.snapshot().single().id
        store.update(0, frame, 1, emptyList(), listOf(owner.elementId), 2)
        store.prune(600_000, owner); assertTrue(store.snapshot().isEmpty())
        store.update(0, frame, 2, listOf(video()), emptyList(), 3)
        store.reset(); store.prune(600_001, owner)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun identicalSourcesInTwoFramesRemainDistinct() {
        val store = LiveVideoSessionRegistry(7)
        val first = store.openFrame("same-document", "https://one.test", false)!!
        val second = store.openFrame("same-document", "https://two.test", false)!!
        store.update(0, first, 0, listOf(video()), emptyList(), 1)
        store.update(0, second, 0, listOf(video()), emptyList(), 1)
        assertEquals(2, store.snapshot().map { it.id }.distinct().size)
        assertEquals(setOf("https://one.test", "https://two.test"), store.snapshot().map { it.frameOrigin }.toSet())
    }

    @Test fun tabsHaveIndependentIdentityEvenForTheSameElement() {
        val one = LiveVideoSessionRegistry(1)
        val two = LiveVideoSessionRegistry(2)
        val a = one.openFrame("document", "https://test", true)!!
        val b = two.openFrame("document", "https://test", true)!!
        one.update(0, a, 0, listOf(video()), emptyList(), 1)
        two.update(0, b, 0, listOf(video()), emptyList(), 1)
        assertNotEquals(one.snapshot().single().id, two.snapshot().single().id)
    }

    @Test fun lowerQualityReplacesLiveDimensionsWithoutChangingTheSourceUrl() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("document", "https://test", true)!!
        store.update(0, frame, 0, listOf(video().copy(videoWidth = 1920, videoHeight = 1080)), emptyList(), 1)
        store.update(0, frame, 1, listOf(video().copy(videoWidth = 640, videoHeight = 360)), emptyList(), 2)
        assertEquals(640, store.snapshot().single().state.videoWidth)
        assertEquals("blob:https://test/video", store.snapshot().single().state.source)
    }

    @Test fun sourceReplacementUpdatesTheSameElementSession() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("document", "https://test", true)!!
        store.update(0, frame, 0, listOf(video()), emptyList(), 1)
        val id = store.snapshot().single().id
        store.update(0, frame, 1, listOf(video().copy(source = "https://cdn.test/new.mp4", sourceRevision = 2)), emptyList(), 2)
        assertEquals(id, store.snapshot().single().id)
        assertEquals(2L, store.snapshot().single().state.sourceRevision)
    }

    @Test fun navigationInvalidatesOldFramesAndLateUpdates() {
        val store = LiveVideoSessionRegistry(1)
        val old = store.openFrame("old", "https://test", true)!!
        store.update(0, old, 0, listOf(video()), emptyList(), 1)
        store.reset()
        assertTrue(store.snapshot().isEmpty())
        assertFalse(store.update(0, old, 1, listOf(video()), emptyList(), 2))
        val current = store.openFrame("new", "https://test", true)!!
        assertFalse(store.update(0, current, 0, listOf(video()), emptyList(), 2))
        assertTrue(store.update(1, current, 0, listOf(video()), emptyList(), 2))
    }

    @Test fun rejectsReplayedOrOutOfOrderSnapshots() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        assertTrue(store.update(0, frame, 4, listOf(video()), emptyList(), 1))
        assertFalse(store.update(0, frame, 4, emptyList(), emptyList(), 2))
        assertFalse(store.update(0, frame, 3, emptyList(), emptyList(), 2))
        assertEquals(1, store.snapshot().size)
    }

    @Test fun fullSnapshotsRemoveMissingElementsEvenAfterFastReplacement() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        store.update(0, frame, 0, listOf(video("old")), emptyList(), 1)
        store.update(0, frame, 1, listOf(video("new")), emptyList(), 2)
        assertEquals("new", store.snapshot().single().id.elementId)
        store.update(0, frame, 2, emptyList(), emptyList(), 3)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun frameRetirementDoesNotRemoveOtherFrames() {
        val store = LiveVideoSessionRegistry(1)
        val first = store.openFrame("one", "https://test", true)!!
        val second = store.openFrame("two", "https://test", false)!!
        store.update(0, first, 0, listOf(video()), emptyList(), 1)
        store.update(0, second, 0, listOf(video()), emptyList(), 1)
        store.closeFrame(first)
        assertEquals(second, store.snapshot().single().id.frameId)
    }

    @Test fun expiredElementsCannotRemainActiveIndefinitely() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        store.update(0, frame, 0, listOf(video()), emptyList(), 1)
        store.prune(46_000)
        assertNull(store.active())
    }

    @Test fun latestInteractionWinsOverAnAutoplayPreview() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        val preview = video("preview").copy(bounds = bounds.copy(width = 1920.0, height = 1080.0))
        store.update(0, frame, 0, listOf(preview, video("chosen").copy(interacted = true)), emptyList(), 100)
        assertEquals("chosen", store.active()?.id?.elementId)
        store.update(0, frame, 1, listOf(preview, video("chosen")), emptyList(), 200)
        assertEquals("chosen", store.active()?.id?.elementId)
    }

    @Test fun pausedHiddenAndEndedVideosAreExcludedFromActiveSelection() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        store.update(0, frame, 0, listOf(video("paused").copy(playing = false),
            video("hidden").copy(visible = false), video("ended").copy(ended = true)), emptyList(), 100)
        assertNull(store.active())
    }

    @Test fun frameAndElementCountsStayBounded() {
        val store = LiveVideoSessionRegistry(1)
        repeat(LiveVideoSessionRegistry.MAX_FRAMES) { index ->
            val frame = store.openFrame("doc-$index", "https://test", false)!!
            store.update(0, frame, 0, (0 until 8).map { video("v$it") }, emptyList(), 1)
        }
        assertEquals(LiveVideoSessionRegistry.MAX_SESSIONS, store.snapshot().size)
        assertNull(store.openFrame("too-many", "https://test", false))
    }

    @Test fun rejectsOversizedOrInvalidElementBatches() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc", "https://test", true)!!
        assertFalse(store.update(0, frame, 0, (0..8).map { video("v$it") }, emptyList(), 1))
        assertFalse(store.update(0, frame, 0, listOf(video("")), emptyList(), 1))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun durationAndBufferedDataDoNotInventASeekableRange() {
        val sample = video().copy(durationMs = 45_000, seekable = emptyList(),
            buffered = listOf(VideoTimeRange(0, 45_000)))
        assertNull(sample.seekTarget(10_000))
        assertEquals(LiveVideoSeekEvidence.FINITE_DURATION_ONLY, sample.seekEvidence)
        assertEquals(LiveVideoSeekEvidence.UNKNOWN, sample.copy(durationMs = null).seekEvidence)
    }

    @Test fun seekingClampsToTheNearestActualRangeIncludingHoles() {
        val video = video().copy(seekable = listOf(VideoTimeRange(5_000, 10_000), VideoTimeRange(20_000, 30_000)))
        assertEquals(5_000L, video.seekTarget(-1))
        assertEquals(10_000L, video.seekTarget(14_000))
        assertEquals(20_000L, video.seekTarget(18_000))
        assertEquals(25_000L, video.seekTarget(25_000))
        assertEquals(30_000L, video.seekTarget(Long.MAX_VALUE))
    }

    private val bounds = VideoFrameBounds(0.0, 0.0, 640.0, 360.0, 1080.0, 1920.0)
    private fun video(id: String = "video") = LiveVideoState(id, "blob:https://test/video",
        LiveVideoSourceKind.BLOB, 12_000, 45_000, true, false, false, false, 1.0,
        1280, 720, true, 1.0, bounds, listOf(VideoTimeRange(0, 45_000)),
        listOf(VideoTimeRange(0, 20_000)), 4, 1, false)
}
