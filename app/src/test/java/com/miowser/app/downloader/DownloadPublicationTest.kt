package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPublicationTest {
    @Test fun pauseErrorAndCompletionFlushExactCountersDespiteSampling() {
        for (status in listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.COMPLETED)) {
            val download = Download(1, "https://example.test/video", "https://example.test/",
                "test", "https://example.test/", null, DownloadKind.DIRECT_FILE, "video", "video")
            download.status = DownloadStatus.DOWNLOADING
            repeat(1_000) { download.addProgress(7) }
            download.status = status
            assertEquals(status, download.status)
            assertEquals(7_000L, download.bytesDownloaded)
        }
    }
}
