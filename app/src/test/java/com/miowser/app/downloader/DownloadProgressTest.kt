package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun formatsSeconds() {
        assertEquals("45s remaining", formatRemainingTime(45))
    }

    @Test
    fun roundsPartialMinutesUp() {
        assertEquals("2 min remaining", formatRemainingTime(61))
    }

    @Test
    fun formatsHoursAndMinutes() {
        assertEquals("2 hr 6 min remaining", formatRemainingTime(7_501))
    }

    @Test
    fun roundsNearlyCompleteHourToNextHour() {
        assertEquals("2 hr remaining", formatRemainingTime(7_199))
    }

    @Test
    fun formatsTimeSpentAcrossMinutesAndHours() {
        assertEquals("45s spent", formatSpentTime(45))
        assertEquals("2m 5s spent", formatSpentTime(125))
        assertEquals("2h 5m spent", formatSpentTime(7_500))
    }

    @Test
    fun combinesRemainingAndSpentTime() {
        val download = Download(
            id = 1L,
            sourceUrl = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            userAgent = "test",
            referer = "https://example.com",
            cookies = null,
            kind = DownloadKind.DIRECT_FILE,
            displayName = "video",
            outputBaseName = "video"
        )
        download.restoreElapsedTime(65_000L)
        download.status = DownloadStatus.COMPLETED

        assertEquals("1m 5s spent", download.timeProgressLabel())
    }
}
