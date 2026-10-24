package com.miowser.app.library

import org.junit.Assert.*
import org.junit.Test

class DurableSubtitleFormatTest {
    @Test fun keepsWebvttAndSubripIncludingBom() {
        assertEquals("vtt", durableSubtitleFormat("\uFEFFWEBVTT\n\n00:00.000 --> 00:01.000\nText"))
        assertEquals("srt", durableSubtitleFormat("\uFEFF1\n00:00:00,050 --> 00:00:01,125\nText"))
    }
    @Test fun rejectsUnrelatedOrMisleadingDocuments() {
        assertNull(durableSubtitleFormat("WEBVTT-not-a-caption"))
        assertNull(durableSubtitleFormat("<html>Session expired</html>"))
    }
}
