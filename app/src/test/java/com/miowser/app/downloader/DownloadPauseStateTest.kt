package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPauseStateTest {
    private fun download() = Download(
        id = 1L,
        sourceUrl = "https://example.com/video.mp4",
        pageUrl = "https://example.com/watch",
        userAgent = "test",
        referer = "https://example.com/watch",
        cookies = null,
        kind = DownloadKind.DIRECT_FILE,
        displayName = "video.mp4",
        outputBaseName = "video"
    )

    @Test
    fun `active download pauses and resumes its previous stage`() {
        val download = download().apply { status = DownloadStatus.DOWNLOADING }
        assertTrue(download.requestPause())
        assertEquals(DownloadStatus.PAUSED, download.status)
        assertTrue(download.requestResume())
        assertEquals(DownloadStatus.DOWNLOADING, download.status)
    }

    @Test
    fun `completed download cannot be paused`() {
        val download = download().apply { status = DownloadStatus.COMPLETED }
        assertFalse(download.requestPause())
        assertEquals(DownloadStatus.COMPLETED, download.status)
    }

    @Test
    fun `restored pause resumes as downloading`() {
        val download = download()
        download.restorePausedState()
        assertEquals(DownloadStatus.PAUSED, download.status)
        assertTrue(download.requestResume())
        assertEquals(DownloadStatus.DOWNLOADING, download.status)
    }
}
