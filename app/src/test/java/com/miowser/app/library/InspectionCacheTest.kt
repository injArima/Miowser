package com.miowser.app.library

import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InspectionCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private var time = 1_000L
    private fun cache(root: File) = InspectionCache(root) { time }.also { it.prepare() }

    @Test fun factKeysKeepTheStableSha256FormatAcrossKindsAndRepeatedLookups() {
        val digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals("available:$digest", LocalInspectionCache.key("available", "abc"))
        assertEquals("metadata:$digest", LocalInspectionCache.key("metadata", "abc"))
        assertEquals("available:$digest", LocalInspectionCache.key("available", "abc"))
    }

    @Test fun factsSurviveProcessRestartIncludingKnownZeroMetadata() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        val ticket = first.ticket()
        assertTrue(first.put("available", "true", 1_000, ticket))
        assertTrue(first.put("metadata", "true:0:true", 1_000, ticket))
        assertTrue(first.put("type", "MEDIA", 1_000, ticket))
        first.flush()
        val reopened = cache(directory)
        assertEquals("true", reopened.peek("available"))
        assertEquals("true:0:true", reopened.peek("metadata"))
        assertEquals("MEDIA", reopened.peek("type"))
    }

    @Test fun warmPrepareKeepsTheMirrorAndDoesNotReloadTheIndex() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        first.put("available", "true", 1_000, first.ticket())
        first.flush()
        val epoch = first.epoch
        File(directory, "facts.bin").writeText("damaged afterwards")
        assertEquals(epoch, first.prepare())
        assertEquals("true", first.peek("available"))
    }

    @Test fun positiveAndFailureExpiryUseTheirOwnLifetimes() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        first.put("good", "true", 10_000, first.ticket())
        first.put("bad", "false", 100, first.ticket())
        time += 100
        assertNull(first.peek("bad"))
        assertEquals("true", first.peek("good"))
        first.flush()
        assertNull(cache(directory).peek("bad"))
        time += 9_900
        assertNull(first.peek("good"))
    }

    @Test fun clearCacheDropsMemoryAndDiskWithoutTouchingUserDataOrAcceptingLateProbes() {
        val directory = temporary.newFolder("cache")
        val userData = temporary.newFile("library-records").apply { writeText("keep") }
        val first = cache(directory)
        first.put("available", "true", 1_000, first.ticket())
        first.flush()
        val ticket = first.ticket()
        val epoch = first.epoch
        assertTrue(directory.deleteRecursively())
        first.flush()
        assertFalse(directory.exists())
        assertTrue(first.prepare() > epoch)
        assertNull(first.peek("available"))
        assertFalse(first.put("available", "true", 1_000, ticket))
        assertEquals("keep", userData.readText())
    }

    @Test fun aLostNamespaceRejectsTheOldIndexEvenIfThatIndexRemains() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        first.put("available", "true", 1_000, first.ticket())
        first.flush()
        assertTrue(File(directory, "generation").delete())
        first.prepare()
        assertNull(first.peek("available"))
        first.put("fresh", "new", 1_000, first.ticket())
        first.flush()
        assertEquals("new", cache(directory).peek("fresh"))
    }

    @Test fun invalidationKeepsOtherFactsAndRejectsTheOldProducerTicket() {
        val first = cache(temporary.newFolder("cache"))
        first.put("uri-a", "old", 1_000, first.ticket())
        first.put("uri-b", "keep", 1_000, first.ticket())
        val oldTicket = first.ticket()
        first.invalidate(listOf("uri-a"))
        assertNull(first.peek("uri-a"))
        assertEquals("keep", first.peek("uri-b"))
        assertFalse(first.put("uri-a", "stale", 1_000, oldTicket))
        assertNull(first.invalidate(listOf("uri-b"), oldTicket))
        assertEquals("keep", first.peek("uri-b"))
        assertTrue(first.put("uri-a", "relinked", 1_000, first.ticket()))
    }

    @Test fun corruptedAndOversizedIndicesRebuildSafely() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        first.put("old", "value", 1_000, first.ticket())
        first.flush()
        File(directory, "facts.bin").writeText("broken")
        assertNull(cache(directory).peek("old"))
        java.io.RandomAccessFile(File(directory, "facts.bin"), "rw").use { it.setLength(5L * 1024 * 1024) }
        val recovered = cache(directory)
        recovered.put("new", "value", 1_000, recovered.ticket())
        recovered.flush()
        assertEquals("value", cache(directory).peek("new"))
    }

    @Test fun entryLimitAndOversizedValuesCannotGrowWithoutBounds() {
        val first = cache(temporary.newFolder("cache"))
        repeat(InspectionCache.MAX_ENTRIES + 1) { first.put("$it", "value", 1_000, first.ticket()) }
        assertNull(first.peek("0"))
        assertEquals("value", first.peek("${InspectionCache.MAX_ENTRIES}"))
        assertFalse(first.put("huge", "x".repeat(4_097), 1_000, first.ticket()))
        first.flush()
    }

    @Test fun excessiveRecordCountsAndTruncatedFilesAreNotTrusted() {
        val directory = temporary.newFolder("cache")
        cache(directory)
        DataOutputStream(File(directory, "facts.bin").outputStream()).use {
            it.writeInt(1); it.writeUTF(File(directory, "generation").readText())
            it.writeInt(InspectionCache.MAX_ENTRIES + 1)
        }
        val rebuilt = cache(directory)
        assertNull(rebuilt.peek("anything"))
        rebuilt.put("valid", "yes", 1_000, rebuilt.ticket())
        rebuilt.flush()
        java.io.RandomAccessFile(File(directory, "facts.bin"), "rw").use { it.setLength(8) }
        assertNull(cache(directory).peek("valid"))
    }

    @Test fun retainedBytesAreBoundedEvenBelowTheEntryLimit() {
        val directory = temporary.newFolder("cache")
        val first = cache(directory)
        repeat(300) { first.put("$it", "x".repeat(4_000), 1_000, first.ticket()) }
        assertNull(first.peek("0"))
        assertEquals(4_000, first.peek("299")?.length)
        first.flush()
        assertTrue(File(directory, "facts.bin").length() < 4L * 1024 * 1024)
    }
}
