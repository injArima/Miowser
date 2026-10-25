package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class LiveVideoSessionsTest {
    @Test fun aLongBackgroundOrPipSessionProtectsOnlyItsExactOwnerFromLeaseExpiry() {
        val store = LiveVideoSessionRegistry(7)
        val frame = store.openFrame("doc")!!
        val otherFrame = store.openFrame("other")!!
        store.update(0, frame, 0, listOf(video(), video().copy(elementId = "sibling")), 1)
        store.update(0, otherFrame, 0, listOf(video()), 1)
        val owner = store.snapshot().first().id
        store.prune(600_000, owner)
        assertEquals(listOf(owner), store.snapshot().map { it.id })
        store.prune(600_001)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun protectionDoesNotResurrectRemovedElementsOrSurviveDocumentNavigation() {
        val store = LiveVideoSessionRegistry(7)
        val frame = store.openFrame("doc")!!
        store.update(0, frame, 0, listOf(video()), 1)
        val owner = store.snapshot().single().id
        store.update(0, frame, 1, emptyList(), 2)
        store.prune(600_000, owner); assertTrue(store.snapshot().isEmpty())
        store.update(0, frame, 2, listOf(video()), 3)
        store.reset(); store.prune(600_001, owner)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun identicalElementsInTwoFramesRemainDistinct() {
        val store = LiveVideoSessionRegistry(7)
        val first = store.openFrame("same-document")!!
        val second = store.openFrame("same-document")!!
        store.update(0, first, 0, listOf(video()), 1)
        store.update(0, second, 0, listOf(video()), 1)
        assertEquals(2, store.snapshot().map { it.id }.distinct().size)
    }

    @Test fun tabsHaveIndependentIdentityEvenForTheSameElement() {
        val one = LiveVideoSessionRegistry(1)
        val two = LiveVideoSessionRegistry(2)
        val a = one.openFrame("document")!!
        val b = two.openFrame("document")!!
        one.update(0, a, 0, listOf(video()), 1)
        two.update(0, b, 0, listOf(video()), 1)
        assertNotEquals(one.snapshot().single().id, two.snapshot().single().id)
    }

    @Test fun lowerQualityReplacesLiveDimensions() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("document")!!
        store.update(0, frame, 0, listOf(video().copy(videoWidth = 1920, videoHeight = 1080)), 1)
        store.update(0, frame, 1, listOf(video().copy(videoWidth = 640, videoHeight = 360)), 2)
        assertEquals(640, store.snapshot().single().state.videoWidth)
        assertEquals(360, store.snapshot().single().state.videoHeight)
    }

    @Test fun sourceReplacementUpdatesTheSameElementSession() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("document")!!
        store.update(0, frame, 0, listOf(video()), 1)
        val id = store.snapshot().single().id
        store.update(0, frame, 1, listOf(video().copy(sourceRevision = 2)), 2)
        assertEquals(id, store.snapshot().single().id)
        assertEquals(2L, store.snapshot().single().state.sourceRevision)
    }

    @Test fun navigationInvalidatesOldFramesAndLateUpdates() {
        val store = LiveVideoSessionRegistry(1)
        val old = store.openFrame("old")!!
        store.update(0, old, 0, listOf(video()), 1)
        store.reset()
        assertTrue(store.snapshot().isEmpty())
        assertFalse(store.update(0, old, 1, listOf(video()), 2))
        val current = store.openFrame("new")!!
        assertFalse(store.update(0, current, 0, listOf(video()), 2))
        assertTrue(store.update(1, current, 0, listOf(video()), 2))
    }

    @Test fun rejectsReplayedOrOutOfOrderSnapshots() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc")!!
        assertTrue(store.update(0, frame, 4, listOf(video()), 1))
        assertFalse(store.update(0, frame, 4, emptyList(), 2))
        assertFalse(store.update(0, frame, 3, emptyList(), 2))
        assertEquals(1, store.snapshot().size)
    }

    @Test fun fullSnapshotsRemoveMissingElementsEvenAfterFastReplacement() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc")!!
        store.update(0, frame, 0, listOf(video("old")), 1)
        store.update(0, frame, 1, listOf(video("new")), 2)
        assertEquals("new", store.snapshot().single().id.elementId)
        store.update(0, frame, 2, emptyList(), 3)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun frameRetirementDoesNotRemoveOtherFrames() {
        val store = LiveVideoSessionRegistry(1)
        val first = store.openFrame("one")!!
        val second = store.openFrame("two")!!
        store.update(0, first, 0, listOf(video()), 1)
        store.update(0, second, 0, listOf(video()), 1)
        store.closeFrame(first)
        assertEquals(second, store.snapshot().single().id.frameId)
    }

    @Test fun expiredSessionsAreRemoved() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc")!!
        store.update(0, frame, 0, listOf(video()), 1)
        store.prune(46_000)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun frameAndElementCountsStayBounded() {
        val store = LiveVideoSessionRegistry(1)
        repeat(LiveVideoSessionRegistry.MAX_FRAMES) { index ->
            val frame = store.openFrame("doc-$index")!!
            store.update(0, frame, 0, (0 until 8).map { video("v$it") }, 1)
        }
        assertEquals(LiveVideoSessionRegistry.MAX_SESSIONS, store.snapshot().size)
        assertNull(store.openFrame("too-many"))
    }

    @Test fun rejectsOversizedOrInvalidElementBatches() {
        val store = LiveVideoSessionRegistry(1)
        val frame = store.openFrame("doc")!!
        assertFalse(store.update(0, frame, 0, (0..8).map { video("v$it") }, 1))
        assertFalse(store.update(0, frame, 0, listOf(video("")), 1))
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun durationAndBufferedDataDoNotInventASeekableRange() {
        val sample = video().copy(durationMs = 45_000, seekable = emptyList(),
            buffered = listOf(VideoTimeRange(0, 45_000)))
        assertNull(sample.seekTarget(10_000))
    }

    @Test fun seekingClampsToTheNearestActualRangeIncludingHoles() {
        val video = video().copy(seekable = listOf(VideoTimeRange(5_000, 10_000), VideoTimeRange(20_000, 30_000)))
        assertEquals(5_000L, video.seekTarget(-1))
        assertEquals(10_000L, video.seekTarget(14_000))
        assertEquals(20_000L, video.seekTarget(18_000))
        assertEquals(25_000L, video.seekTarget(25_000))
        assertEquals(30_000L, video.seekTarget(Long.MAX_VALUE))
    }

    private fun video(id: String = "video") = LiveVideoState(id, 12_000, 45_000,
        true, false, false, 1.0, 1280, 720, listOf(VideoTimeRange(0, 45_000)),
        listOf(VideoTimeRange(0, 20_000)), 4, 1)
}
