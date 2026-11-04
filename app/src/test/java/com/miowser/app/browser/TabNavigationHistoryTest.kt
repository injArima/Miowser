package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabNavigationHistoryTest {
    @Test fun backwardHistoryStartsAtCurrentAndMovesTowardOldest() {
        assertEquals(listOf(3, 2, 1, 0), directedNavigationIndices(6, 3, forward = false))
    }

    @Test fun forwardHistoryStartsAtCurrentAndMovesTowardNewest() {
        assertEquals(listOf(2, 3, 4), directedNavigationIndices(5, 2, forward = true))
    }

    @Test fun historyIsBoundedAndRejectsInvalidSnapshots() {
        assertEquals(listOf(4, 3), directedNavigationIndices(6, 4, forward = false, limit = 2))
        assertEquals(emptyList<Int>(), directedNavigationIndices(0, 0, forward = false))
        assertEquals(emptyList<Int>(), directedNavigationIndices(3, 3, forward = true))
        assertEquals(emptyList<Int>(), directedNavigationIndices(3, 1, forward = true, limit = 0))
    }
}
