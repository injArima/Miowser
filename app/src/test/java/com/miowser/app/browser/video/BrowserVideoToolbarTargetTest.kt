package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class BrowserVideoToolbarTargetTest {
    private fun session(frame: Long = 1, action: VideoAssistantAction = VideoAssistantAction.TOOLBAR) =
        LiveVideoSession(LiveVideoId(7, 3, frame, "doc-$frame", "v1"), "https://test", frame == 1L,
            LiveVideoState("v1", "blob:test", LiveVideoSourceKind.BLOB, 12000, 45000,
                true, false, false, false, 1.0, 1280, 720, true, 1.0,
                VideoFrameBounds(0.0, 0.0, 640.0, 360.0, 640.0, 900.0), emptyList(), emptyList(),
                4, 1, false, assistantAction = action), 1000, 0)

    @Test fun clocksAndBufferUpdatesDoNotChangeToolbarTarget() {
        val first = session()
        val next = first.copy(updatedAtMs = 2000, state = first.state.copy(positionMs = 13000,
            buffered = listOf(VideoTimeRange(0, 25000)), buffering = true))
        assertEquals(browserVideoToolbarTarget(listOf(first)), browserVideoToolbarTarget(listOf(next)))
        assertNotEquals(browserVideoToolbarTarget(listOf(first)),
            browserVideoToolbarTarget(listOf(next.copy(state = next.state.copy(sourceRevision = 2)))))
    }

    @Test fun anActionAlreadyVisibleBelowTheChosenVideoDoesNotGetADuplicateToolbarAction() {
        val row = session(action = VideoAssistantAction.ROW).copy(lastInteractionMs = 200)
        val embedded = session(frame = 2).copy(lastInteractionMs = 100)
        assertNull(browserVideoToolbarTarget(listOf(row, embedded)))
        assertEquals(embedded.id, browserVideoToolbarTarget(listOf(row, embedded.copy(lastInteractionMs = 300)))?.id)
    }

    @Test fun pausedHiddenEndedOrRetiredVideosCannotOfferToolbarPlayback() {
        val original = session()
        val states = listOf(original.state.copy(playing = false), original.state.copy(visible = false),
            original.state.copy(ended = true), original.state.copy(readyState = 1),
            original.state.copy(videoWidth = 0), original.state.copy(assistantAction = VideoAssistantAction.NONE))
        states.forEach { assertNull(browserVideoToolbarTarget(listOf(original.copy(state = it)))) }
        assertNull(browserVideoToolbarTarget(emptyList()))
    }

    @Test fun frameAndNavigationIdentitySurviveProjection() {
        val first = session()
        val next = first.copy(id = first.id.copy(navigation = 4, frameId = 9, documentId = "new-doc"))
        assertNotEquals(browserVideoToolbarTarget(listOf(first)), browserVideoToolbarTarget(listOf(next)))
        assertEquals(next.id, browserVideoToolbarTarget(listOf(next))?.id)
    }
}
