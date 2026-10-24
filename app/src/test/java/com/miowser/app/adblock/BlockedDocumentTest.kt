package com.miowser.app.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedDocumentTest {
    @Test fun blockedNavigationExplainsTheBlockAndHowToRecover() {
        val html = blockedDocumentHtml("||example.test^")
        assertTrue(html.contains("Page blocked by ad blocker"))
        assertTrue(html.contains("site settings"))
        assertTrue(html.contains("||example.test^"))
    }

    @Test fun ruleTextCannotBecomeMarkupOrUnboundedPageContent() {
        val html = blockedDocumentHtml("<script>alert('x')</script>&" + "x".repeat(10_000))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.length < 4_000)
    }

    @Test fun blockingSourcesAppearInAnEscapedDropdown() {
        val html = blockedDocumentHtml("||example.test^", listOf(
            FilterRuleSource("first", "First list"),
            FilterRuleSource("second", "<script>bad()</script>", "https://lists.example/filter?a=1&b=2")
        ))
        assertTrue(html.contains("<summary>Blocking lists (2)</summary>"))
        assertTrue(html.contains("First list"))
        assertTrue(html.contains("&lt;script&gt;bad()&lt;/script&gt;"))
        assertTrue(html.contains("filter?a=1&amp;b=2"))
        assertFalse(html.contains("<script>"))
    }
}
