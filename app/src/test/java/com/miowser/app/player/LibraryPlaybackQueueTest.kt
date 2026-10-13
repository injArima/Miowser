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
        val queue = LibraryPlaybackQueue.fromVisible(listOf(first, second, third))

        assertEquals(listOf(first, second, third), queue.entries(listOf(third, first, second)))
        assertEquals(third, queue.neighbor(second.identityKey, listOf(third, first, second), 1))
        assertEquals(first, queue.neighbor(second.identityKey, listOf(third, first, second), -1))
        assertNull(queue.neighbor(third.identityKey, listOf(third, first, second), 1))
    }

    @Test fun `skips entries removed after the queue was created`() {
        val first = entry(1)
        val second = entry(2)
        val third = entry(3)
        val queue = LibraryPlaybackQueue.fromVisible(listOf(first, second, third))

        assertEquals(third, queue.neighbor(first.identityKey, listOf(first, third), 1))
        assertEquals(listOf(first, third), queue.entries(listOf(first, third)))
    }
}
