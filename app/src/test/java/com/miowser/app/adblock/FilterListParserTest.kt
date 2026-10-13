package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListParserTest {

    @Test
    fun `parses a plain domain-anchored network rule`() {
        val rule = FilterListParser.parseNetworkRule("||doubleclick.net^", FilterSource.SUBSCRIBED)
        assertFalse(rule.isException)
        assertEquals("doubleclick.net", rule.anchoredDomain)
        assertTrue(rule.options.includedResourceTypes.isEmpty())
    }

    @Test
    fun `plain domain rule skips regex compilation entirely`() {
        // The ~82%-of-EasyList fast path: "||domain^" with nothing else needs no regex.
        val rule = FilterListParser.parseNetworkRule("||doubleclick.net^", FilterSource.SUBSCRIBED)
        assertNull(rule.regex)
    }

    @Test
    fun `domain rule with additional path content still gets a compiled regex`() {
        val rule = FilterListParser.parseNetworkRule("||example.com/ads/*", FilterSource.SUBSCRIBED)
        assertNotNull(rule.regex)
        assertEquals("example.com", rule.anchoredDomain) // still indexed, just also pattern-matched
    }

    @Test
    fun `domain rule with options after the anchor still counts as plain (options aren't part of the pattern)`() {
        val rule = FilterListParser.parseNetworkRule("||doubleclick.net^\$third-party", FilterSource.SUBSCRIBED)
        assertNull(rule.regex)
        assertEquals(true, rule.options.thirdPartyOnly)
    }

    @Test
    fun `parses an exception rule`() {
        val rule = FilterListParser.parseNetworkRule(
            "@@||youtube.com/get_video_info?\$xmlhttprequest,domain=music.youtube.com",
            FilterSource.SUBSCRIBED
        )
        assertTrue(rule.isException)
        assertTrue(ResourceType.XHR in rule.options.includedResourceTypes)
        assertTrue("music.youtube.com" in rule.options.includedDomains)
    }

    @Test
    fun `parses third-party and resource-type options`() {
        val rule = FilterListParser.parseNetworkRule("||tracker.com^\$third-party,image,script", FilterSource.SUBSCRIBED)
        assertEquals(true, rule.options.thirdPartyOnly)
        assertTrue(ResourceType.IMAGE in rule.options.includedResourceTypes)
        assertTrue(ResourceType.SCRIPT in rule.options.includedResourceTypes)
    }

    @Test
    fun `parses domain exclusion option`() {
        val rule = FilterListParser.parseNetworkRule("||ads.example.com^\$domain=~good.com", FilterSource.SUBSCRIBED)
        assertTrue("good.com" in rule.options.excludedDomains)
    }

    @Test
    fun `parses negated resource-type options as exclusions, not inclusions`() {
        // Real EasyList rule shape (seen live on CNN): "$~image,~xmlhttprequest,domain=...".
        // Previously "~image" fell through to the ignored-tokens branch entirely, silently
        // dropping the constraint instead of applying it as an exclusion.
        val rule = FilterListParser.parseNetworkRule("||ads.example.com^\$~image,~xmlhttprequest", FilterSource.SUBSCRIBED)
        assertTrue(rule.options.includedResourceTypes.isEmpty())
        assertTrue(ResourceType.IMAGE in rule.options.excludedResourceTypes)
        assertTrue(ResourceType.XHR in rule.options.excludedResourceTypes)
    }

    @Test
    fun `mixes included and excluded resource types on the same rule`() {
        val rule = FilterListParser.parseNetworkRule("||ads.example.com^\$script,~image", FilterSource.SUBSCRIBED)
        assertTrue(ResourceType.SCRIPT in rule.options.includedResourceTypes)
        assertTrue(ResourceType.IMAGE in rule.options.excludedResourceTypes)
    }

    @Test
    fun `parse splits network and cosmetic rules and skips regex-literal lines`() {
        val text = """
            ! a comment
            [Adblock Plus 2.0]
            ||ads.example.com^
            example.com##.ad-banner
            ###generic-id
            /banner\d+\.gif/
        """.trimIndent()

        val result = FilterListParser.parse(text, FilterSource.SUBSCRIBED)

        assertEquals(1, result.networkRules.size)
        assertEquals(2, result.cosmeticRules.size)
        assertEquals(1, result.skippedRegex)
    }

    @Test
    fun `cosmetic rule with domain is scoped, generic rule is not`() {
        val result = FilterListParser.parse("example.com##.ad-banner\n###generic-id", FilterSource.SUBSCRIBED)
        val scoped = result.cosmeticRules.first { it.selector == ".ad-banner" }
        val generic = result.cosmeticRules.first { it.selector == "#generic-id" }
        assertEquals("example.com", scoped.domain)
        assertEquals(null, generic.domain)
    }

    @Test
    fun `cosmetic rule listing multiple domains expands into one rule per domain`() {
        val result = FilterListParser.parse("foo.com,bar.com##.ad", FilterSource.SUBSCRIBED)
        assertEquals(2, result.cosmeticRules.size)
        assertTrue(result.cosmeticRules.any { it.domain == "foo.com" })
        assertTrue(result.cosmeticRules.any { it.domain == "bar.com" })
    }

    @Test
    fun `extended procedural cosmetic syntax is skipped rather than mishandled`() {
        val result = FilterListParser.parse("example.com##div:xpath(//aside)", FilterSource.SUBSCRIBED)
        assertTrue(result.cosmeticRules.isEmpty())
    }

    @Test
    fun `native CSS has selector remains available to cosmetic rules`() {
        val result = FilterListParser.parse("##.fh-wrapper:has(.fh-ad-label)", FilterSource.MANUAL)
        assertEquals(1, result.cosmeticRules.size)
        assertEquals(".fh-wrapper:has(.fh-ad-label)", result.cosmeticRules.single().selector)
    }

    @Test
    fun `cosmetic rule with a domain exclusion keeps the inclusion and records the exclusion`() {
        val result = FilterListParser.parse("example.com,~sub.example.com##.ad", FilterSource.SUBSCRIBED)
        assertEquals(1, result.cosmeticRules.size)
        val rule = result.cosmeticRules.single()
        assertEquals("example.com", rule.domain)
        assertEquals(listOf("sub.example.com"), rule.excludedDomains)
    }

    @Test
    fun `cosmetic rule with only exclusions is treated as generic, carrying the exclusion`() {
        val result = FilterListParser.parse("~foo.com##.ad", FilterSource.SUBSCRIBED)
        assertEquals(1, result.cosmeticRules.size)
        val rule = result.cosmeticRules.single()
        assertEquals(null, rule.domain)
        assertEquals(listOf("foo.com"), rule.excludedDomains)
    }

    @Test
    fun `cosmetic rule with multiple included domains shares one exclusion list across all of them`() {
        val result = FilterListParser.parse("a.com,b.com,~c.com##.ad", FilterSource.SUBSCRIBED)
        assertEquals(2, result.cosmeticRules.size)
        assertTrue(result.cosmeticRules.all { it.excludedDomains == listOf("c.com") })
    }
}
