package com.miowser.app.browser

import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HubDownloadSectionTest {
    private fun download(id: Long, status: DownloadStatus) = Download(
        id = id,
        sourceUrl = "https://example.com/$id.mp4",
        pageUrl = "https://example.com/watch/$id",
        userAgent = "test",
        referer = "https://example.com",
        cookies = null,
        kind = DownloadKind.DIRECT_FILE,
        displayName = "$id.mp4",
        outputBaseName = id.toString()
    ).apply { this.status = status }

    @Test
    fun `downloading section keeps every recoverable and unfinished status`() {
        val downloads = DownloadStatus.entries.mapIndexed { index, status -> download(index.toLong(), status) }

        val visible = downloadsForSection(downloads, HubDownloadSection.DOWNLOADING)

        assertEquals(DownloadStatus.entries.filter { it != DownloadStatus.COMPLETED }, visible.map { it.status })
    }

    @Test
    fun `downloaded section contains only completed files`() {
        val downloads = DownloadStatus.entries.mapIndexed { index, status -> download(index.toLong(), status) }

        val visible = downloadsForSection(downloads, HubDownloadSection.DOWNLOADED)

        assertEquals(listOf(DownloadStatus.COMPLETED), visible.map { it.status })
    }

    @Test
    fun `download card uses a clean website name`() {
        val download = download(1L, DownloadStatus.COMPLETED)

        assertEquals("example.com", downloadSourceLabel(download))
    }

    @Test
    fun `download card falls back when neither address has a website`() {
        val download = Download(
            id = 1L,
            sourceUrl = "video.mp4",
            pageUrl = "",
            userAgent = "test",
            referer = "",
            cookies = null,
            kind = DownloadKind.DIRECT_FILE,
            displayName = "video.mp4",
            outputBaseName = "video"
        )

        assertEquals("Downloaded file", downloadSourceLabel(download))
    }
}
