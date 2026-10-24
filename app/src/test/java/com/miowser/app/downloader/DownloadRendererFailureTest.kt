package com.miowser.app.downloader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DownloadRendererFailureTest {
    private fun download() = Download(
        id = 1L, sourceUrl = "blob:https://example.com/video", pageUrl = "https://example.com",
        userAgent = "test", referer = "https://example.com", cookies = null,
        kind = DownloadKind.WEBVIEW_RESOURCE, displayName = "video.mp4", outputBaseName = "video"
    )

    @Test fun rendererLossAfterCheckpointReleaseRemainsAFailure() {
        val download = download()
        download.status = DownloadStatus.DOWNLOADING
        download.rendererTransfer = { error("Retired renderer must not be called") }
        assertTrue(download.requestPause())
        val ready = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val worker = Thread {
            ready.countDown()
            try { download.awaitIfPaused() } catch (_: DownloadYieldException) { }
            returned.countDown()
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(ready.await(1, TimeUnit.SECONDS))
            assertTrue(returned.await(1, TimeUnit.SECONDS))
            download.invalidateRendererSource("Renderer stopped. Reload the page.")
            assertEquals(DownloadStatus.FAILED, download.status)
            assertEquals("Renderer stopped. Reload the page.", download.errorMessage)
            assertFalse(download.isCanceled)
            assertTrue(download.rendererSourceUnavailable)
            assertNull(download.rendererTransfer)
            assertFalse(download.requestResume())
        } finally {
            download.requestCancel()
            worker.join(1_000)
        }
    }

    @Test fun completedFileKeepsItsStateAndProgressWhenRendererDies() {
        val download = download()
        download.bytesDownloaded = 123L
        download.status = DownloadStatus.COMPLETED
        download.rendererTransfer = { }
        download.invalidateRendererSource("Renderer stopped")

        assertEquals(DownloadStatus.COMPLETED, download.status)
        assertEquals(123L, download.bytesDownloaded)
        assertNull(download.errorMessage)
        assertNull(download.rendererTransfer)
    }

    @Test fun queuedTransferFailsImmediatelyAndDropsRendererReferences() {
        val download = download()
        download.rendererTransfer = { error("Must not execute") }
        download.invalidateRendererSource("Renderer stopped")

        assertEquals(DownloadStatus.FAILED, download.status)
        assertTrue(download.rendererSourceUnavailable)
        assertFalse(download.isCanceled)
        assertNull(download.rendererTransfer)
    }
}
