package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyOutputNameTest {
    @Test
    fun `first candidate keeps requested name`() {
        assertEquals("movie.mp4", legacyOutputName("movie.mp4", 1))
        assertEquals("archive", legacyOutputName("archive", 1))
    }

    @Test
    fun `subsequent candidates add suffix before extension`() {
        assertEquals("movie (2).mp4", legacyOutputName("movie.mp4", 2))
        assertEquals("movie.part (3).mp4", legacyOutputName("movie.part.mp4", 3))
        assertEquals("archive (4)", legacyOutputName("archive", 4))
        assertEquals(".hidden (2)", legacyOutputName(".hidden", 2))
    }
}
