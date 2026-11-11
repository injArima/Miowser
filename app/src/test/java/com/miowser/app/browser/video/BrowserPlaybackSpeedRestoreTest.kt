package com.miowser.app.browser.video

import com.miowser.app.player.validPlayerPlaybackSpeed
import org.junit.Assert.*
import org.junit.Test

class BrowserPlaybackSpeedRestoreTest {
    @Test fun restoresAnExplicitChoiceAfterASourceReset() {
        val restore = BrowserPlaybackSpeedRestore(1.37)
        assertEquals(1.37, restore.next(1.0, false)!!, 0.0)
        assertNull(restore.next(1.37, false))
        assertEquals(1.37, restore.next(1.0, false)!!, 0.0)
    }

    @Test fun leavesWebsiteSpeedAloneUntilTheUserHasMadeAChoice() {
        assertNull(BrowserPlaybackSpeedRestore(null).next(1.25, false))
    }

    @Test fun temporaryTwoTimesHoldDoesNotConsumeRestoreAttempts() {
        val restore = BrowserPlaybackSpeedRestore(1.25)
        repeat(8) { assertNull(restore.next(2.0, true)) }
        assertEquals(1.25, restore.next(2.0, false)!!, 0.0)
        assertNull(restore.next(1.25, false))
    }

    @Test fun stopsRetryingIfTheWebsiteKeepsRejectingTheSpeed() {
        val restore = BrowserPlaybackSpeedRestore(1.5)
        repeat(3) { assertEquals(1.5, restore.next(1.0, false)!!, 0.0) }
        repeat(5) { assertNull(restore.next(1.0, false)) }
        assertEquals(1.5, BrowserPlaybackSpeedRestore(1.5).next(1.0, false)!!, 0.0)
    }

    @Test fun normalSpeedIsAnExplicitPersistableChoiceAndFloatNoiseDoesNotCauseRetries() {
        assertEquals(1.0, BrowserPlaybackSpeedRestore(1.0).next(2.0, false)!!, 0.0)
        assertNull(BrowserPlaybackSpeedRestore(1.15f.toDouble()).next(1.15, false))
    }

    @Test fun rejectsInvalidSavedSpeeds() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 0.24f, 3.01f)) {
            assertFalse(validPlayerPlaybackSpeed(value))
            assertNull(BrowserPlaybackSpeedRestore(value.toDouble()).next(1.0, false))
        }
        assertTrue(validPlayerPlaybackSpeed(0.25f))
        assertTrue(validPlayerPlaybackSpeed(3f))
    }
}
