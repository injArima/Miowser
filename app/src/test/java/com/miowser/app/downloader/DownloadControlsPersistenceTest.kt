package com.miowser.app.downloader

import org.junit.Assert.*
import org.junit.Test

class DownloadControlsPersistenceTest {
    @Test fun measuresNetworkBytesIndependentlyOfSegmentProgress() {
        val download = Download(1, "https://example.test/file", "", "", "", null,
            DownloadKind.HLS, "file", "file")
        download.restoreTransferred(20)
        download.status = DownloadStatus.DOWNLOADING
        download.metered(java.io.ByteArrayInputStream(ByteArray(100))).use { input ->
            assertEquals(0, input.read())
            assertEquals(99, input.read(ByteArray(200)))
            assertEquals(-1, input.read())
        }
        download.bytesDownloaded = 1
        download.totalBytes = 4
        download.publishProgress(force = true)
        assertEquals(120L, download.transferredBytes)
        assertEquals(1L, download.bytesDownloaded)
        assertEquals(25, download.progressPercent())
        assertTrue(download.transferInformation()!!.contains("1/4 segments"))
    }

    private fun record() = SavedDownload(7, "https://example.test/file", "https://example.test", DownloadKind.DIRECT_FILE,
        "file", "file", DownloadStatus.PAUSED, 42, 99, null, connectionLimit = 2, queueRank = -4, transferredBytes = 100)

    @Test fun preservesConnectionOverrideQueuePriorityAndTransferredBytes() {
        val saved = record()
        assertEquals(saved, DownloadCodec.decode(DownloadCodec.encode(listOf(saved))).single())
    }

    @Test fun oldRecordsAcquireSafeDefaultsWithoutLosingRequestContext() {
        val old = DownloadCodec.encode(listOf(record())).split('\t').take(16).joinToString("\t")
        val restored = DownloadCodec.decode(old).single()
        assertEquals(0, restored.connectionLimit)
        assertEquals(7L, restored.queueRank)
        assertEquals(0L, restored.transferredBytes)
        assertEquals(42L, restored.bytesDownloaded)
        assertEquals(DownloadStatus.PAUSED, restored.status)
    }
}
