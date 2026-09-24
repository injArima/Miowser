package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDialCodecTest {
    @Test
    fun roundTripPreservesOrderAndValues() {
        val entries = listOf(
            SpeedDial(4L, "Example", "https://example.com"),
            SpeedDial(9L, "News", "https://news.example.com/latest", folderId = 3L)
        )

        assertEquals(entries, SpeedDialCodec.decode(SpeedDialCodec.encode(entries)))
    }

    @Test
    fun codecSanitizesRecordDelimiters() {
        val decoded = SpeedDialCodec.decode(
            SpeedDialCodec.encode(listOf(SpeedDial(1L, "One\nTwo", "https://example.com/a\tb")))
        ).single()

        assertEquals("One Two", decoded.title)
        assertEquals("https://example.com/a b", decoded.url)
    }

    @Test
    fun malformedRecordsAreSkipped() {
        val decoded = SpeedDialCodec.decode(
            "bad\trecord\n2\tValid\thttps://example.com\n3\t\thttps://empty-title.example"
        )

        assertEquals(listOf(SpeedDial(2L, "Valid", "https://example.com")), decoded)
    }

    @Test
    fun oldThreeFieldRecordsRestoreAtHomepageRoot() {
        assertEquals(
            listOf(SpeedDial(2L, "Legacy", "https://example.com", folderId = null)),
            SpeedDialCodec.decode("2\tLegacy\thttps://example.com")
        )
    }

    @Test
    fun foldersRoundTripAndMalformedRowsAreSkipped() {
        val folders = listOf(SpeedDialFolder(2L, "News"), SpeedDialFolder(7L, "Watch later"))
        val encoded = SpeedDialFolderCodec.encode(folders) + "\ninvalid"

        assertEquals(folders, SpeedDialFolderCodec.decode(encoded))
    }

    @Test
    fun domainAddressIsNormalizedToHttps() {
        assertEquals("https://example.com/path", normalizeSpeedDialAddress("example.com/path"))
    }

    @Test
    fun nonWebSchemesAreRejected() {
        assertEquals("", normalizeSpeedDialAddress("javascript:alert(1)"))
    }

    @Test
    fun defaultTitleUsesCleanHostname() {
        assertEquals("example.com", defaultSpeedDialTitle("https://www.example.com/watch"))
    }

    @Test
    fun emptyCodecInputHasNoEntries() {
        assertTrue(SpeedDialCodec.decode("").isEmpty())
    }

    @Test
    fun speedDialSortSupportsAddedAndBothNameDirections() {
        val entries = listOf(
            SpeedDial(1L, "ignored", "https://zebra.example"),
            SpeedDial(2L, "ignored", "https://Alpha.example"),
            SpeedDial(3L, "ignored", "https://middle.example")
        )

        assertEquals(entries, sortSpeedDials(entries, SpeedDialSort.ADDED))
        assertEquals(
            listOf(2L, 3L, 1L),
            sortSpeedDials(entries, SpeedDialSort.NAME_ASCENDING).map(SpeedDial::id)
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            sortSpeedDials(entries, SpeedDialSort.NAME_DESCENDING).map(SpeedDial::id)
        )
    }
}
