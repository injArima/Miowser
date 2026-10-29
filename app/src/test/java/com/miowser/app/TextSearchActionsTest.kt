package com.miowser.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSearchActionsTest {
    @Test
    fun normalizedQuery_trimsAndRejectsBlankText() {
        assertEquals("Miowser search", TextSearchActions.normalizedQuery("  Miowser search  "))
        assertNull(TextSearchActions.normalizedQuery("   \n\t"))
        assertNull(TextSearchActions.normalizedQuery(null))
    }

    @Test
    fun normalizedQuery_boundsExternalInput() {
        assertEquals(8_192, TextSearchActions.normalizedQuery("x".repeat(9_000))?.length)
    }
}

