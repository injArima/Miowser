package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderCodecTest {
    @Test fun `folders preserve unicode names and automatic domains`() {
        val folders = listOf(LibraryFolder("one", "Anime 日本語", "example.com"), LibraryFolder("two", "Manual"))
        assertEquals(folders, LibraryFolderCodec.decode(LibraryFolderCodec.encode(folders)))
    }

    @Test fun `website matching accepts subdomains but not lookalikes`() {
        assertEquals("example.com", libraryDomain("https://www.example.com/watch/1"))
        assertTrue(libraryDomainMatches("https://media.example.com/watch", "example.com"))
        assertFalse(libraryDomainMatches("https://notexample.com/watch", "example.com"))
        assertFalse(libraryDomainMatches("https://example.com.evil.test/watch", "example.com"))
    }
}
