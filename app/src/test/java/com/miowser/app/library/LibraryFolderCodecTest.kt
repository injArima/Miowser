package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderCodecTest {
    @Test fun `folders preserve unicode names and automatic domains`() {
        val folders = listOf(
            LibraryFolder("one", "Anime 日本語", "example.com", additionalDomains = listOf("anime.example", "shows.test"),
                websiteOperator = LibraryRuleOperator.ALL),
            LibraryFolder("two", "Manual", parentId = "one", titleRule = LibraryTitleRule(
                LibraryTitleSource.COMBINED, LibraryRuleOperator.ANY, listOf("Steel Ball Run", "JoJo 日本語")))
        )
        assertEquals(folders, LibraryFolderCodec.decode(LibraryFolderCodec.encode(folders)))
    }

    @Test fun `legacy flat folders remain readable and invalid parents return to root`() {
        assertEquals(
            listOf(LibraryFolder("old", "Old")),
            LibraryFolderCodec.decode("old\tT2xk\t")
        )
        assertEquals(
            listOf(LibraryFolder("old", "Old", "example.com")),
            LibraryFolderCodec.decode("old\tT2xk\texample.com\t")
        )
        assertEquals(
            listOf(LibraryFolder("child", "Child")),
            LibraryFolderCodec.decode("child\tQ2hpbGQ\t\tmissing")
        )
        assertEquals(
            listOf(LibraryFolder("a", "A"), LibraryFolder("b", "B")),
            LibraryFolderCodec.decode("a\tQQ\t\tb\nb\tQg\t\ta")
        )
    }

    @Test fun `website routing takes priority over the default folder`() {
        val folders = listOf(
            LibraryFolder("default", "Default"),
            LibraryFolder("site", "Site", "example.com", additionalDomains = listOf("another.test")),
            LibraryFolder("specific", "Specific", "video.example.com", "site")
        )
        assertEquals("default", chooseLibraryFolder(folders, "https://elsewhere.test/video", "default"))
        assertEquals("site", chooseLibraryFolder(folders, "https://example.com/video", "default"))
        assertEquals("site", chooseLibraryFolder(folders, "https://sub.another.test/video", "default"))
        assertEquals("specific", chooseLibraryFolder(folders, "https://video.example.com/video", "default"))
        assertEquals(null, chooseLibraryFolder(folders, null, "deleted"))
    }

    @Test fun `website matching accepts subdomains but not lookalikes`() {
        assertEquals("example.com", libraryDomain("https://www.example.com/watch/1"))
        assertTrue(libraryDomainMatches("https://media.example.com/watch", "example.com"))
        assertFalse(libraryDomainMatches("https://notexample.com/watch", "example.com"))
        assertFalse(libraryDomainMatches("https://example.com.evil.test/watch", "example.com"))
    }
}
