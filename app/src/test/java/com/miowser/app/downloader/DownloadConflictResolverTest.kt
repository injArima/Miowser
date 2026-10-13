package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadConflictResolverTest {
    private fun download(
        id: Long,
        url: String = "https://example.com/movie.mp4",
        name: String = "movie.mp4",
        baseName: String = name.substringBeforeLast('.'),
        status: DownloadStatus = DownloadStatus.QUEUED
    ) = Download(
        id = id,
        sourceUrl = url,
        pageUrl = "https://example.com/watch",
        userAgent = "test",
        referer = "https://example.com/watch",
        cookies = null,
        kind = DownloadKind.DIRECT_FILE,
        displayName = name,
        outputBaseName = baseName,
        suggestedExtension = name.substringAfterLast('.', "").ifBlank { null }
    ).apply { this.status = status }

    @Test
    fun `same active source takes priority over filename conflict`() {
        val active = download(1, status = DownloadStatus.DOWNLOADING)
        val completed = download(2, url = "https://cdn.example.com/other.mp4", status = DownloadStatus.COMPLETED)

        val conflict = findDownloadConflict(listOf(completed, active), download(3))

        assertEquals(active.id, conflict?.existing?.id)
        assertEquals(DownloadConflictReason.SAME_SOURCE, conflict?.reason)
    }

    @Test
    fun `completed same source receives full source conflict choices`() {
        val completed = download(1, status = DownloadStatus.COMPLETED)

        val conflict = findDownloadConflict(listOf(completed), download(2))

        assertEquals(completed.id, conflict?.existing?.id)
        assertEquals(DownloadConflictReason.SAME_SOURCE, conflict?.reason)
    }

    @Test
    fun `same source with a different name still uses source conflict actions`() {
        val existing = download(
            id = 1,
            name = "old-name.mp4",
            baseName = "old-name",
            status = DownloadStatus.COMPLETED
        )
        val candidate = download(
            id = 2,
            name = "new-name.mp4",
            baseName = "new-name"
        )

        val conflict = findDownloadConflict(listOf(existing), candidate)

        assertEquals(DownloadConflictReason.SAME_SOURCE, conflict?.reason)
    }

    @Test
    fun `completed matching filename is detected across different URLs`() {
        val completed = download(1, url = "https://one.example/movie.mp4", status = DownloadStatus.COMPLETED)
        val candidate = download(2, url = "https://two.example/movie.mp4")

        assertEquals(
            DownloadConflictReason.SAME_NAME,
            findDownloadConflict(listOf(completed), candidate)?.reason
        )
    }

    @Test
    fun `active matching filename with a different URL reserves the output name`() {
        val active = download(
            id = 1,
            url = "https://one.example/movie.mp4",
            status = DownloadStatus.DOWNLOADING
        )
        val candidate = download(id = 2, url = "https://two.example/movie.mp4")

        val conflict = findDownloadConflict(listOf(active), candidate)

        assertEquals(DownloadConflictReason.SAME_NAME, conflict?.reason)
        assertEquals(active.id, conflict?.existing?.id)
    }

    @Test
    fun `same URL always wins when another record also matches the filename`() {
        val nameOnly = download(
            id = 1,
            url = "https://other.example/movie.mp4",
            status = DownloadStatus.COMPLETED
        )
        val sameUrl = download(
            id = 2,
            name = "different-name.mp4",
            baseName = "different-name",
            status = DownloadStatus.PAUSED
        )
        val candidate = download(id = 3)

        val conflict = findDownloadConflict(listOf(nameOnly, sameUrl), candidate)

        assertEquals(DownloadConflictReason.SAME_SOURCE, conflict?.reason)
        assertEquals(sameUrl.id, conflict?.existing?.id)
    }

    @Test
    fun `failed and canceled records do not reserve a filename`() {
        val failed = download(1, url = "https://one.example/movie.mp4", status = DownloadStatus.FAILED)
        val canceled = download(2, url = "https://two.example/movie.mp4", status = DownloadStatus.CANCELED)
        val candidate = download(3, url = "https://three.example/movie.mp4")

        assertNull(findDownloadConflict(listOf(failed, canceled), candidate))
    }

    @Test
    fun `URL comparison ignores fragment and normalizes default port`() {
        assertEquals(
            true,
            sameDownloadSource(
                "HTTPS://Example.com:443/file.mp4#one",
                "https://example.com/file.mp4#two"
            )
        )
    }

    @Test
    fun `different source and filename has no conflict`() {
        val completed = download(1, status = DownloadStatus.COMPLETED)
        val candidate = download(2, url = "https://example.com/other.webm", name = "other.webm")

        assertNull(findDownloadConflict(listOf(completed), candidate))
    }

    @Test
    fun `renamed copy skips names already in use and pins final basename`() {
        val candidate = download(1)
        val existing = listOf(
            download(2, status = DownloadStatus.COMPLETED),
            download(3, name = "movie (2).mp4", baseName = "movie (2)", status = DownloadStatus.COMPLETED)
        )

        val renamed = renamedDownloadCopy(candidate, existing)

        assertEquals("movie (3).mp4", renamed.displayName)
        assertEquals("movie (3)", renamed.outputBaseName)
        assertEquals("movie (3)", renamed.preferredOutputBaseName)
    }
}
