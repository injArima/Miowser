package com.miowser.app.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTitleRuleTest {
    @Test fun `whole phrases ignore case and punctuation`() {
        val rule = LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ALL, listOf("Steel Ball Run"))
        assertTrue(matchesLibraryTitleRule(rule, "STEEL-Ball Run episode 1", null))
        assertFalse(matchesLibraryTitleRule(rule, "Steel cartoon Run", null))
        assertFalse(matchesLibraryTitleRule(
            LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ANY, listOf("art")),
            "cartoon", null
        ))
    }

    @Test fun `combined source lets AND phrases appear in different titles`() {
        val rule = LibraryTitleRule(
            LibraryTitleSource.COMBINED, LibraryRuleOperator.ALL,
            listOf("JoJo", "episode 2")
        )
        assertTrue(matchesLibraryTitleRule(rule, "JoJo", "Watch Episode-2 online"))
        assertFalse(matchesLibraryTitleRule(rule.copy(source = LibraryTitleSource.MEDIA), "JoJo", "Episode 2"))
        assertFalse(matchesLibraryTitleRule(rule.copy(source = LibraryTitleSource.PAGE), "JoJo", "Episode 2"))
    }

    @Test fun `OR matches any phrase but an empty rule never matches`() {
        val rule = LibraryTitleRule(
            LibraryTitleSource.PAGE, LibraryRuleOperator.ANY,
            listOf("JoJo", "Steel Ball Run")
        )
        assertTrue(matchesLibraryTitleRule(rule, null, "steel-ball run"))
        assertFalse(matchesLibraryTitleRule(rule, null, "other video"))
        assertFalse(matchesLibraryTitleRule(rule.copy(phrases = emptyList()), null, "JoJo"))
    }
}
