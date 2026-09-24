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
        errorMessage: String? = null,
        savedUri: String? = null,
        suggestedExtension: String? = null,
        elapsedActiveMillis: Long = 0L,
        preferredOutputBaseName: String? = null,
        userAgent: String = "Miowser/Test",
        referer: String = "https://example.com/watch"
    ) = SavedDownload(
        id = id, sourceUrl = sourceUrl, pageUrl = pageUrl, kind = kind, displayName = displayName,
        outputBaseName = outputBaseName, status = status, bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes, errorMessage = errorMessage, savedUri = savedUri,
        suggestedExtension = suggestedExtension, elapsedActiveMillis = elapsedActiveMillis,
        preferredOutputBaseName = preferredOutputBaseName, userAgent = userAgent, referer = referer
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
            sample(id = 3L, kind = DownloadKind.DASH, status = DownloadStatus.CANCELED),
            sample(id = 4L, kind = DownloadKind.WEBVIEW_RESOURCE, status = DownloadStatus.COMPLETED)
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

    @Test
    fun `round-trips a completed download's savedUri`() {
        val original = listOf(sample(status = DownloadStatus.COMPLETED, savedUri = "content://media/external/downloads/123"))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals("content://media/external/downloads/123", decoded[0].savedUri)
    }

    @Test
    fun `round-trips a webpage download's suggested extension`() {
        val original = listOf(sample(displayName = "archive.torrent", suggestedExtension = "torrent"))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals("torrent", decoded[0].suggestedExtension)
    }

    @Test
    fun `round-trips active time spent`() {
        val original = listOf(sample(elapsedActiveMillis = 123_456L))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(123_456L, decoded.single().elapsedActiveMillis)
    }

    @Test
    fun `round-trips a user-selected renamed output basename`() {
        val original = listOf(sample(preferredOutputBaseName = "video (2)"))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals("video (2)", decoded.single().preferredOutputBaseName)
    }

    @Test
    fun `round-trips non-secret request metadata used for recovery`() {
        val decoded = DownloadCodec.decode(DownloadCodec.encode(listOf(sample())))
        assertEquals("Miowser/Test", decoded.single().userAgent)
        assertEquals("https://example.com/watch", decoded.single().referer)
    }

    @Test
    fun `decodes a pre-elapsed-time record with zero active time`() {
        val preElapsed = DownloadCodec.encode(listOf(sample(elapsedActiveMillis = 99_000L)))
            .split('\t').take(12).joinToString("\t")
        val decoded = DownloadCodec.decode(preElapsed)
        assertEquals(1, decoded.size)
        assertEquals(0L, decoded.single().elapsedActiveMillis)
    }

    @Test
    fun `decodes a pre-preferred-name record and keeps active time`() {
        val previousFormat = DownloadCodec.encode(
            listOf(sample(elapsedActiveMillis = 99_000L, preferredOutputBaseName = "video (2)"))
        ).split('\t').take(13).joinToString("\t")
        val decoded = DownloadCodec.decode(previousFormat)
        assertEquals(1, decoded.size)
        assertEquals(99_000L, decoded.single().elapsedActiveMillis)
        assertEquals(null, decoded.single().preferredOutputBaseName)
    }

    @Test
    fun `decodes a pre-savedUri record (one field short) with savedUri null`() {
        // Exactly what a real pre-upgrade persisted file looks like: the same 10 fields
        // DownloadCodec wrote before savedUri existed, with no 11th field at all — not just a
        // blank one. Must still decode the other 9 fields correctly, not just avoid crashing.
        val legacyLine = listOf(
            "7", "https://example.com/old.mp4", "https://example.com/watch-old", "DIRECT_FILE",
            "old video", "old_video", "COMPLETED", "500", "500", ""
        ).joinToString("\t")
        val decoded = DownloadCodec.decode(legacyLine)
        assertEquals(1, decoded.size)
        assertEquals(7L, decoded[0].id)
        assertEquals("old video", decoded[0].displayName)
        assertEquals(DownloadStatus.COMPLETED, decoded[0].status)
        assertEquals(null, decoded[0].savedUri)
    }

    @Test
    fun `decodes a current-format record with a blank savedUri field as null`() {
        val original = listOf(sample(status = DownloadStatus.FAILED, savedUri = null))
        val decoded = DownloadCodec.decode(DownloadCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals(null, decoded[0].savedUri)
    }

    @Test
    fun `decodes the previous fourteen-field format without request metadata`() {
        val previousFormat = DownloadCodec.encode(listOf(sample())).split('\t').take(14).joinToString("\t")
        val decoded = DownloadCodec.decode(previousFormat)
        assertEquals(1, decoded.size)
        assertEquals("", decoded.single().userAgent)
        assertEquals("", decoded.single().referer)
    }
}

class DownloadSignatureTest {
    @Test
    fun `recognizes MP4 data even when the server calls it a generic binary`() {
        val bytes = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        assertEquals("mp4", sniffDownloadedFileExtension(bytes))
    }

    @Test
    fun `recognizes zip-compatible payloads`() {
        assertEquals("zip", sniffDownloadedFileExtension(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
    }
}
