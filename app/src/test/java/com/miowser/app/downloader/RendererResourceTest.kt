package com.miowser.app.downloader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererResourceTest {
    @Test
    fun `classifies only renderer-owned URL schemes`() {
        assertEquals(RendererResourceKind.BLOB, rendererResourceKind("blob:https://example.com/id"))
        assertEquals(RendererResourceKind.DATA, rendererResourceKind("DATA:text/plain,hello"))
        assertEquals(null, rendererResourceKind("https://example.com/file.mp4"))
    }

    @Test
    fun `decodes base64 data with its declared MIME type`() {
        val result = DataUrlDecoder.decode("data:video/mp4;base64,AAECAw==", 32)
        assertTrue(result is DataUrlDecodeResult.Success)
        result as DataUrlDecodeResult.Success
        assertEquals("video/mp4", result.value.mimeType)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), result.value.bytes)
    }

    @Test
    fun `percent decoder preserves plus and arbitrary escaped bytes`() {
        val result = DataUrlDecoder.decode("data:application/octet-stream,A+B%00%FF", 32)
        assertTrue(result is DataUrlDecodeResult.Success)
        result as DataUrlDecodeResult.Success
        assertArrayEquals(byteArrayOf('A'.code.toByte(), '+'.code.toByte(), 'B'.code.toByte(), 0, 0xff.toByte()), result.value.bytes)
    }

    @Test
    fun `uses the standard default MIME type`() {
        val result = DataUrlDecoder.decode("data:,hello", 32) as DataUrlDecodeResult.Success
        assertEquals("text/plain", result.value.mimeType)
    }

    @Test
    fun `rejects malformed and oversized payloads`() {
        assertTrue(DataUrlDecoder.decode("data:text/plain;base64,%%%", 32) is DataUrlDecodeResult.Failure)
        assertTrue(DataUrlDecoder.decode("data:text/plain,12345", 4) is DataUrlDecodeResult.Failure)
    }

    @Test
    fun `blob ledger accepts only ordered bounded chunks with the declared total`() {
        val ledger = BlobTransferLedger(maxTotalBytes = 10, maxChunkBytes = 4)
        assertTrue(ledger.begin(10))
        assertTrue(ledger.acceptChunk(0, 4))
        assertTrue(ledger.acceptChunk(1, 4))
        assertTrue(ledger.acceptChunk(2, 2))
        assertTrue(ledger.isComplete())
    }

    @Test
    fun `blob ledger rejects duplicate out of order oversized and overrun chunks`() {
        val ledger = BlobTransferLedger(maxTotalBytes = 8, maxChunkBytes = 4)
        assertTrue(ledger.begin(8))
        assertTrue(!ledger.acceptChunk(1, 4))
        assertTrue(!ledger.acceptChunk(0, 5))
        assertTrue(ledger.acceptChunk(0, 4))
        assertTrue(!ledger.acceptChunk(0, 4))
        assertTrue(!ledger.acceptChunk(1, 5))
        assertTrue(!ledger.isComplete())
    }

    @Test
    fun `renamed queue copies keep their renderer transfer handoff`() {
        val download = Download(
            id = 1L,
            sourceUrl = "blob:https://example.com/id",
            pageUrl = "https://example.com",
            userAgent = "ua",
            referer = "https://example.com",
            cookies = null,
            kind = DownloadKind.WEBVIEW_RESOURCE,
            displayName = "video.mp4",
            outputBaseName = "video"
        )
        val handoff: (Download) -> Unit = {}
        download.rendererTransfer = handoff

        assertSame(handoff, download.copyForQueue(displayName = "video (2).mp4").rendererTransfer)
    }
}
