package com.miowser.app.library

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalArtworkDiskCacheTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun recentReadsSurviveEvictionAndTheCacheCanReopen() {
        val root = files.newFolder()
        val cache = LocalArtworkDiskCache(root, maxBytes = 8, maxEntries = 10)
        for (name in listOf("a.jpg", "b.jpg")) cache.write(name) { it.write(byteArrayOf(1, 2, 3, 4)); true }
        assertEquals(4, cache.read("a.jpg") { it.readBytes().size })
        cache.write("c.jpg") { it.write(byteArrayOf(5, 6, 7, 8)); true }
        assertFalse(File(root, "b.jpg").exists())
        assertEquals(8L, root.listFiles()!!.sumOf(File::length))
        val reopened = LocalArtworkDiskCache(root, maxBytes = 8, maxEntries = 10)
        assertEquals(listOf<Byte>(5, 6, 7, 8), reopened.read("c.jpg") { it.readBytes().toList() })
    }

    @Test fun incompleteOversizedAndCorruptPreviewsAreDiscarded() {
        val root = files.newFolder()
        File(root, "orphan.jpg.part").writeText("partial")
        val cache = LocalArtworkDiskCache(root, maxBytes = 4)
        cache.write("failed.jpg") { it.write(1); false }
        cache.write("large.jpg") { it.write(ByteArray(5)); true }
        cache.write("corrupt.jpg") { it.write(1); true }
        assertNull(cache.read<String>("corrupt.jpg") { null })
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun revisionsSeparatePreviewsAndUnknownFileMetadataCannotPersist() {
        assertNotEquals(localArtworkKey("content://media/video/5", "media:version:2"),
            localArtworkKey("content://media/video/5", "media:version:3"))
        assertTrue(hasReliableArtworkRevision("media:version:3"))
        assertTrue(hasReliableArtworkRevision("1234:5000"))
        assertFalse(hasReliableArtworkRevision("0:5000"))
        assertFalse(hasReliableArtworkRevision("unversioned"))
    }
}
