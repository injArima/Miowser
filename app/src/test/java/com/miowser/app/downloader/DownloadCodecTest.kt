package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCodecTest {

    private fun sample(
        id: Long = 1L,
        sourceUrl: String = "https://example.com/video.mp4",
        pageUrl: String = "https://example.com/watch",
        kind: DownloadKind = DownloadKind.DIRECT_FILE,
        displayName: String = "video",
        outputBaseName: String = "video",
        status: DownloadStatus = DownloadStatus.COMPLETED,
        bytesDownloaded: Long = 1024L,
        totalBytes: Long = 2048L,
        errorMessage: String? = null
    ) = SavedDownload(
        id = id, sourceUrl = sourceUrl, pageUrl = pageUrl, kind = kind, displayName = displayName,
        outputBaseName = outputBaseName, status = status, bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes, errorMessage = errorMessage
    )

    @Test
    fun `round-trips a single download`() {
        val original = listOf(sample())
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips multiple downloads including a failed one with an error message`() {
        val original = listOf(
            sample(id = 1L, status = DownloadStatus.COMPLETED),
            sample(id = 2L, kind = DownloadKind.HLS, status = DownloadStatus.FAILED, errorMessage = "network error"),
            sample(id = 3L, kind = DownloadKind.DASH, status = DownloadStatus.CANCELED)
        )
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `sanitizes delimiter characters that would otherwise corrupt the record shape`() {
        val original = listOf(sample(displayName = "video\twith\ndelimiters"))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].displayName.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `decode of empty string yields no downloads`() {
        assertTrue(DownloadCodec.decode("").isEmpty())
    }

    @Test
    fun `decode skips malformed lines rather than throwing`() {
        val good = sample(id = 42L)
        val decoded = DownloadCodec.decode("not-a-valid-record\n${DownloadCodec.encode(listOf(good))}")
        assertEquals(1, decoded.size)
        assertEquals(good, decoded[0])
    }
}
