package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedFileTypeTest {
    @Test
    fun `APK is routed to installer`() {
        assertEquals(DownloadedFileType.APK, downloadedFileType("Miowser.APK"))
        assertEquals("application/vnd.android.package-archive", mimeTypeForDownloadedFile("Miowser.apk"))
    }

    @Test
    fun `video and audio are routed to rich player`() {
        assertEquals(DownloadedFileType.MEDIA, downloadedFileType("movie.mp4"))
        assertEquals(DownloadedFileType.MEDIA, downloadedFileType("song.flac"))
    }

    @Test
    fun `documents and unknown files use the system handler`() {
        assertEquals(DownloadedFileType.OTHER, downloadedFileType("manual.pdf"))
        assertEquals(DownloadedFileType.OTHER, downloadedFileType("archive.bin"))
    }

    @Test
    fun `saved video with generic bin name uses media content evidence`() {
        assertEquals(
            DownloadedFileType.MEDIA,
            downloadedFileType("download.bin", "application/octet-stream", "mp4", DownloadKind.DIRECT_FILE, "https://example.test/file")
        )
        assertEquals(
            DownloadedFileType.MEDIA,
            downloadedFileType("download.bin", "video/mp4", null, DownloadKind.DIRECT_FILE, "https://example.test/file")
        )
        assertEquals(
            DownloadedFileType.MEDIA,
            downloadedFileType("download.bin", null, null, DownloadKind.HLS, "https://example.test/index.m3u8")
        )
    }

    @Test
    fun `actual document signature is not played despite a video name`() {
        assertEquals(
            DownloadedFileType.OTHER,
            downloadedFileType("misnamed.mp4", "video/mp4", "pdf", DownloadKind.DIRECT_FILE, "https://example.test/file")
        )
    }

    @Test
    fun `apk with zip prefix still uses installer`() {
        assertEquals(
            DownloadedFileType.APK,
            downloadedFileType("installer.apk", "application/vnd.android.package-archive", "zip", DownloadKind.DIRECT_FILE, "https://example.test/file")
        )
        assertEquals(
            DownloadedFileType.APK,
            downloadedFileType("download.bin", "application/octet-stream", "apk", DownloadKind.DIRECT_FILE, "https://example.test/file")
        )
    }
}
