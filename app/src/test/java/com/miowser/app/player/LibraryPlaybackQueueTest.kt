package com.miowser.app.player

import com.miowser.app.grabber.MediaType
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPlaybackQueueTest {
    private fun entry(id: Int) = LibraryEntry(
        mediaUri = "https://example.com/$id.mp4",
        pageUrl = "https://example.com/watch/$id",
        title = "Video $id",
        sourceLabel = "example.com",
        sourceKind = LibrarySourceKind.REMOTE_STREAM,
        mediaType = MediaType.MP4,
        userAgent = null,
        thumbnailUrl = null,
        lastPlayedAt = id.toLong(),
        positionMs = 0,
        durationMs = 0
    )

    @Test fun `retains visible order when library recency changes`() {
        val first = entry(1)
        val second = entry(2)
        val third = entry(3)
        val queue = LibraryPlaybackQueue(listOf(first, second, third).map(LibraryEntry::identityKey))
        val records = listOf(third, first, second).associateBy(LibraryEntry::identityKey)

        assertEquals(listOf(first, second, third), queue.entries(records::get))
        assertEquals(third, queue.neighbor(second.identityKey, 1, records::get))
        assertEquals(first, queue.neighbor(second.identityKey, -1, records::get))
        assertNull(queue.neighbor(third.identityKey, 1, records::get))
    }

    @Test fun `skips entries removed after the queue was created`() {
        val first = entry(1)
        val second = entry(2)
        val third = entry(3)
        val queue = LibraryPlaybackQueue(listOf(first, second, third).map(LibraryEntry::identityKey))
        val records = listOf(first, third).associateBy(LibraryEntry::identityKey)

        assertEquals(third, queue.neighbor(first.identityKey, 1, records::get))
        assertEquals(listOf(first, third), queue.entries(records::get))
    }
}
