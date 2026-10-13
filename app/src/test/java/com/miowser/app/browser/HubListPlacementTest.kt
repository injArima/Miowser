package com.miowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubListPlacementTest {
    private val initial = listOf("folder-1", "a", "b", "c")

    @Test fun confirmedRemovalKeepsSurvivorsInOrder() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        motion.removedByUser("b")
        assertTrue(motion.update(listOf("folder-1", "a", "c"), true))
    }

    @Test fun automaticRemovalAndReorderingSnap() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        assertFalse(motion.update(listOf("folder-1", "a", "c"), true))
        assertFalse(motion.update(listOf("folder-1", "c", "a"), true))
    }

    @Test fun bulkRemovalAndConcurrentInsertionRejectManualHint() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        motion.removedByUser("b")
        assertFalse(motion.update(listOf("folder-1", "a"), true))
        motion.removedByUser("a")
        assertFalse(motion.update(listOf("folder-1", "new"), true))
    }

    @Test fun unrelatedStructuralChangeConsumesHint() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        motion.removedByUser("b")
        assertFalse(motion.update(initial + "new", true))
        assertFalse(motion.update(listOf("folder-1", "a", "c", "new"), true))
    }

    @Test fun survivorReorderingRejectsEvenASingleRemoval() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        motion.removedByUser("b")
        assertFalse(motion.update(listOf("folder-1", "c", "a"), true))
    }

    @Test fun scrollingHiddenAndReducedMotionInvalidatePendingRemoval() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        motion.removedByUser("b")
        assertFalse(motion.update(initial, false))
        assertFalse(motion.update(listOf("folder-1", "a", "c"), true))
    }

    @Test fun freshDestinationAndRestoreHaveNoRemovalHistory() {
        val motion = HubListPlacement()
        assertTrue(motion.update(initial, true))
        assertFalse(motion.update(initial + "restored", true))
        assertFalse(HubListPlacement().update(initial, false))
    }

    @Test fun stableLayoutRearmsAfterBulkSnapForNextManualEdit() {
        val motion = HubListPlacement()
        motion.update(initial, true)
        val bulk = listOf("folder-1", "c", "new")
        assertFalse(motion.update(bulk, true))
        assertTrue(motion.update(bulk, true))
        motion.removedByUser("new")
        assertTrue(motion.update(listOf("folder-1", "c"), true))
    }
}
