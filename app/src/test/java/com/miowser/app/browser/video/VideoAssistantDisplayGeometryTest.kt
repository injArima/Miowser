package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class VideoAssistantDisplayGeometryTest {
    private fun geometry() = VideoAssistantDisplayGeometry(1, .5, .5,
        60.0 / 384, 60.0 / 700.8, 384.0, 700.8)

    @Test fun matchingWebViewViewportKeepsTheRealTapAndNativeFaceAligned() {
        val geometry = geometry()
        assertTrue(geometry.fits(1080, 1971))
        assertEquals(168.75, geometry.width * 1080, .01)
        assertEquals(135.0, geometry.width * 1080 * 48 / 60, .01)
    }
    @Test fun keyboardOrStaleViewportCannotPaintAtTheWrongTapLocation() {
        assertFalse(geometry().fits(1080, 1200))
        assertFalse(geometry().fits(0, 1971))
        assertFalse(geometry().fits(1080, 0))
    }
    @Test fun untrustedGeometryCannotEscapeTheWebViewOrAllocateHugeViews() {
        val geometry = geometry()
        listOf(geometry.copy(left = -.1), geometry.copy(top = 2.0), geometry.copy(width = 99.0),
            geometry.copy(height = 0.0), geometry.copy(left = Double.NaN),
            geometry.copy(viewportWidth = Double.POSITIVE_INFINITY), geometry.copy(version = -1))
            .forEach { assertFalse(it.fits(1080, 1971)) }
    }
    @Test fun portraitMetadataIsRejectedAfterRotationUntilNewGeometryArrives() {
        assertFalse(geometry().fits(1971, 1080))
        assertTrue(VideoAssistantDisplayGeometry(2, .5, .5, 60.0 / 700.8,
            60.0 / 384, 700.8, 384.0).fits(1971, 1080))
    }
}
