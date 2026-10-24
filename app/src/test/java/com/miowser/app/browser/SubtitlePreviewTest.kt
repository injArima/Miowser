package com.miowser.app.browser

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePreviewTest {
    @Test fun previewStopsAtLimitWithoutDiscardingTheReadablePrefix() = runBlocking {
        val input = ByteArrayInputStream("WEBVTT\n00:00:01.000 --> 00:00:02.000\nHello".toByteArray())
        val (bytes, truncated) = readSubtitlePreview(input, limit = 12)
        assertArrayEquals("WEBVTT\n00:00".toByteArray(), bytes)
        assertTrue(truncated)
    }

    @Test fun shortSubtitleIsNotMarkedTruncated() = runBlocking {
        val source = "WEBVTT\n\nHello".toByteArray()
        val (bytes, truncated) = readSubtitlePreview(ByteArrayInputStream(source), limit = 100)
        assertArrayEquals(source, bytes)
        assertFalse(truncated)
    }
}
