package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

/** Repeatable structured-store baseline; timings are observations, not pass/fail thresholds. */
class LibraryCodecScaleMeasurementTest {
    @Test fun reportWholeCollectionCostAt500And5000Entries() {
        for (count in listOf(500, 5_000)) {
            val entries = List(count) { index ->
                LibraryEntry(
                    mediaUri = "https://media.example/$index/video.mp4",
                    pageUrl = "https://media.example/watch/$index",
                    title = "Video $index",
                    sourceLabel = "media.example",
                    sourceKind = LibrarySourceKind.REMOTE_STREAM,
                    mediaType = null,
                    userAgent = null,
                    thumbnailUrl = null,
                    lastPlayedAt = index.toLong(),
                    positionMs = 1_000L,
                    durationMs = 60_000L,
                    folderId = "folder-${index % 20}"
                )
            }
            val encoded = LibraryCodec.encode(entries)
            assertEquals(entries, LibraryCodec.decode(encoded))
            repeat(3) { LibraryCodec.encode(entries); LibraryCodec.decode(encoded) }
            val encodeMs = medianMs { LibraryCodec.encode(entries) }
            val decodeMs = medianMs { LibraryCodec.decode(encoded) }
            val updates = entries.toMutableList()
            val mutationNs = measureNanoTime {
                repeat(100) { turn ->
                    val index = turn * count / 100
                    updates[index] = updates[index].copy(positionMs = turn.toLong())
                    LibraryCodec.encode(updates)
                }
            }
            println("Library codec count=$count bytes=${encoded.toByteArray(Charsets.UTF_8).size} " +
                "encodeMedianMs=$encodeMs decodeMedianMs=$decodeMs " +
                "hundredWholeCollectionUpdatesMs=${mutationNs / 1_000_000.0}")
        }
    }

    private fun medianMs(block: () -> Unit): Double =
        List(7) { measureNanoTime(block) }.sorted()[3] / 1_000_000.0
}
