package com.miowser.app.library

import org.junit.Assert.*
import org.junit.Test

class P2RoutingComparisonTest {
    @Test fun compiledRulesMatchThePreviousRouterAcrossHierarchiesAndTitleVariants() {
        val folders = (0 until 16).map { i -> LibraryFolder("r$i", "Rule $i", domain = if (i % 3 == 0) "site$i.org" else null,
            titleRule = if (i % 3 != 0) LibraryTitleRule(LibraryTitleSource.entries[i % 3],
                LibraryRuleOperator.entries[i % 2], listOf("Steel ball run", "Part $i")) else null) } +
            listOf(LibraryFolder("child", "Child", parentId = "r1", titleRule = LibraryTitleRule(
                LibraryTitleSource.COMBINED, LibraryRuleOperator.ANY, listOf("episode"))))
        val router = CompiledLibraryRouter(folders, null)
        repeat(1000) { i ->
            val facts = LibraryRuleFacts("https://sub.site${i % 16}.org/page", "https://cdn.site${i % 13}.org/file",
                if (i % 2 == 0) "STEEL-Ball Run Part ${i % 16}" else "Cartoon episode $i", "Steel ball run part ${i % 16}")
            assertEquals(baselineRouteLibraryVideo(folders, null, null, facts), router.route(null, facts))
        }
    }
}
