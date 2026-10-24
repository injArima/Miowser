package com.miowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSwipePolicyTest {
    @Test fun verticalAndDiagonalScrollsNeverStartCloseSwipe() {
        assertFalse(startsTabCloseSwipe(0f, 35f, 8f))
        assertFalse(startsTabCloseSwipe(18f, 16f, 8f))
        assertFalse(startsTabCloseSwipe(18f, 10f, 8f))
        assertFalse(startsTabCloseSwipe(7f, 0f, 8f))
        assertTrue(startsTabCloseSwipe(18f, 5f, 8f))
        assertTrue(startsTabCloseSwipe(-18f, 5f, 8f))
    }

    @Test fun closeRequiresLongMostlyHorizontalTravel() {
        assertFalse(commitsTabCloseSwipe(45f, 0f, 160f, 72f))
        assertFalse(commitsTabCloseSwipe(110f, 90f, 160f, 72f))
        assertTrue(commitsTabCloseSwipe(110f, 10f, 160f, 72f))
        assertTrue(commitsTabCloseSwipe(-110f, 10f, 160f, 72f))
    }
}
