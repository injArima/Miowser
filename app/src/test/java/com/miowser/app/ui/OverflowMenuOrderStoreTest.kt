package com.miowser.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OverflowMenuOrderStoreTest {
    @Test
    fun `empty saved order uses the complete default`() {
        assertEquals(DEFAULT_OVERFLOW_MENU_ORDER, normalizeOverflowMenuOrder(emptyList()))
    }

    @Test
    fun `saved order is preserved and newly introduced actions are appended`() {
        val normalized = normalizeOverflowMenuOrder(listOf("SHARE", "HISTORY", "HUB"))

        assertEquals(
            listOf(
                OverflowMenuAction.SHARE,
                OverflowMenuAction.HISTORY,
                OverflowMenuAction.HUB,
                OverflowMenuAction.ADBLOCK,
                OverflowMenuAction.DOWNLOADS,
                OverflowMenuAction.DETECTED,
                OverflowMenuAction.SETTINGS
            ),
            normalized
        )
    }

    @Test
    fun `unknown and duplicate action names are ignored`() {
        val normalized = normalizeOverflowMenuOrder(
            listOf("DOWNLOADS", "REMOVED_ACTION", "DOWNLOADS", "SETTINGS")
        )

        assertEquals(1, normalized.count { it == OverflowMenuAction.DOWNLOADS })
        assertEquals(1, normalized.count { it == OverflowMenuAction.SETTINGS })
        assertEquals(DEFAULT_OVERFLOW_MENU_ORDER.toSet(), normalized.toSet())
    }
}
