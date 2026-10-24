package com.miowser.app.player

import org.junit.Assert.*
import org.junit.Test

class PlayerOverlayStateTest {
    @Test fun pipClearsRetainedModalAndInvalidatesItsExit() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.SUBTITLES)
        state.dismiss()
        val exit = state.revision
        state.clear()
        assertNull(state.target)
        assertNull(state.rendered)
        assertFalse(state.isPresent)
        assertFalse(state.finishExit(PlayerOverlay.SUBTITLES, exit))
        state.open(PlayerOverlay.PLAYLIST)
        assertEquals(PlayerOverlay.PLAYLIST, state.rendered)
        assertFalse(state.finishExit(PlayerOverlay.SUBTITLES, exit))
    }

    @Test fun clearingAnAbsentModalIsIdempotent() {
        val state = PlayerOverlayState()
        val revision = state.revision
        state.clear()
        assertEquals(revision, state.revision)
        state.open(PlayerOverlay.PLAYLIST)
        state.clear()
        val cleared = state.revision
        state.clear()
        assertEquals(cleared, state.revision)
    }

    @Test fun dismissedModalRemainsPresentUntilExitCompletes() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.PLAYLIST)
        state.dismiss()
        assertNull(state.target)
        assertTrue(state.isPresent)
        assertTrue(state.finishExit(PlayerOverlay.PLAYLIST, state.revision))
        assertFalse(state.isPresent)
    }

    @Test fun oldExitCannotRemoveReopenedModal() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.SUBTITLES)
        state.dismiss()
        val oldExit = state.revision
        state.open(PlayerOverlay.SUBTITLES)
        assertFalse(state.finishExit(PlayerOverlay.SUBTITLES, oldExit))
        assertEquals(PlayerOverlay.SUBTITLES, state.rendered)
        assertEquals(PlayerOverlay.SUBTITLES, state.target)
    }

    @Test fun switchingPanelsRendersOneModalAndKeepsOwnershipDuringExit() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.PLAYLIST)
        state.open(PlayerOverlay.SUBTITLES)
        assertEquals(PlayerOverlay.PLAYLIST, state.rendered)
        assertEquals(PlayerOverlay.SUBTITLES, state.target)
        assertFalse(state.finishExit(PlayerOverlay.PLAYLIST, state.revision))
        assertEquals(PlayerOverlay.SUBTITLES, state.rendered)
        assertTrue(state.isPresent)
    }

    @Test fun repeatedBackDoesNotInvalidateCurrentExitOrCompleteTwice() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.PLAYLIST)
        state.dismiss()
        val exit = state.revision
        state.dismiss()
        assertEquals(exit, state.revision)
        assertTrue(state.finishExit(PlayerOverlay.PLAYLIST, exit))
        assertFalse(state.finishExit(PlayerOverlay.PLAYLIST, exit))
    }

    @Test fun oldExitCannotFinishALaterDismissalOfTheSamePanel() {
        val state = PlayerOverlayState()
        state.open(PlayerOverlay.PLAYLIST)
        state.dismiss()
        val oldExit = state.revision
        state.open(PlayerOverlay.PLAYLIST)
        state.dismiss()
        assertFalse(state.finishExit(PlayerOverlay.PLAYLIST, oldExit))
        assertTrue(state.isPresent)
        assertTrue(state.finishExit(PlayerOverlay.PLAYLIST, state.revision))
    }

    @Test fun stalePanelAndOldRequestCannotCloseNewPanel() {
        val oldRequest = PlayerOverlayState()
        oldRequest.open(PlayerOverlay.PLAYLIST)
        oldRequest.dismiss()
        val nextRequest = PlayerOverlayState()
        nextRequest.open(PlayerOverlay.SUBTITLES)
        assertFalse(nextRequest.finishExit(PlayerOverlay.PLAYLIST, oldRequest.revision))
        assertEquals(PlayerOverlay.SUBTITLES, nextRequest.target)
        assertTrue(oldRequest.finishExit(PlayerOverlay.PLAYLIST, oldRequest.revision))
        assertEquals(PlayerOverlay.SUBTITLES, nextRequest.rendered)
    }
}
