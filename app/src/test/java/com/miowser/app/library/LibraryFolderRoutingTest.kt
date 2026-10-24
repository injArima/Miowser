package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderRoutingTest {
    private val facts = LibraryRuleFacts(
        pageUrl = "https://shows.test/watch/1",
        mediaUrl = "https://cdn.test/1.mp4",
        mediaTitle = "Steel-Ball Run Episode 2",
        pageTitle = "JoJo episode 2"
    )

    @Test fun `website AND can match page and media hosts`() {
        val folder = LibraryFolder("sites", "Sites", "shows.test", additionalDomains = listOf("cdn.test"),
            websiteOperator = LibraryRuleOperator.ALL)
        assertTrue(matchesLibraryWebsiteRule(folder, facts))
        assertFalse(matchesLibraryWebsiteRule(folder.copy(additionalDomains = listOf("other.test")), facts))
    }

    @Test fun `root rules take from default then child rules refine only their parent`() {
        val folders = listOf(
            LibraryFolder("default", "Default"),
            LibraryFolder("shows", "Shows", titleRule = LibraryTitleRule(
                LibraryTitleSource.COMBINED, LibraryRuleOperator.ANY, listOf("Steel Ball Run"))),
            LibraryFolder("episodes", "Episodes", parentId = "shows", titleRule = LibraryTitleRule(
                LibraryTitleSource.MEDIA, LibraryRuleOperator.ALL, listOf("Steel Ball Run", "Episode 2"))),
            LibraryFolder("other", "Other", titleRule = LibraryTitleRule(
                LibraryTitleSource.PAGE, LibraryRuleOperator.ANY, listOf("JoJo")))
        )
        assertEquals("episodes", routeLibraryVideo(folders, "default", "default", facts))
        assertEquals("other", routeLibraryVideo(folders, "default", "other", facts))
    }

    @Test fun `website wins over title and more AND phrases wins among title rules`() {
        val title = LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ALL, listOf("Episode 2"))
        val folders = listOf(
            LibraryFolder("default", "Default"),
            LibraryFolder("one", "One", titleRule = title),
            LibraryFolder("two", "Two", titleRule = title.copy(phrases = listOf("Steel Ball Run", "Episode 2"))),
            LibraryFolder("site", "Site", "shows.test")
        )
        assertEquals("site", routeLibraryVideo(folders, "default", "default", facts))
        assertEquals("two", routeLibraryVideo(folders.dropLast(1), "default", "default", facts))
    }

    @Test fun `child recheck stays in its parent even when a root website rule matches`() {
        val folders = listOf(
            LibraryFolder("default", "Default"),
            LibraryFolder("site", "Site", "shows.test"),
            LibraryFolder("child", "Child", parentId = "default", titleRule = LibraryTitleRule(
                LibraryTitleSource.MEDIA, LibraryRuleOperator.ANY, listOf("Steel Ball Run")))
        )
        assertEquals("child", routeLibraryVideo(folders, "default", "default", facts,
            allowRootRouting = false))
    }

    @Test fun `changed title can leave an old branch when routed from default again`() {
        val folders = listOf(
            LibraryFolder("default", "Default"),
            LibraryFolder("steel", "Steel", titleRule = LibraryTitleRule(
                LibraryTitleSource.MEDIA, LibraryRuleOperator.ANY, listOf("Steel Ball Run"))),
            LibraryFolder("jojo", "JoJo", titleRule = LibraryTitleRule(
                LibraryTitleSource.MEDIA, LibraryRuleOperator.ANY, listOf("JoJo")))
        )
        assertEquals("steel", routeLibraryVideo(folders, "default", "default", facts))
        assertEquals("jojo", routeLibraryVideo(folders, "default", "default",
            facts.copy(mediaTitle = "JoJo Episode 3")))
        assertEquals("default", routeLibraryVideo(folders, "default", "default",
            facts.copy(mediaTitle = "Another Show")))
    }
}
