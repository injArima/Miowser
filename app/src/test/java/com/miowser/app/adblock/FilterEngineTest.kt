package com.miowser.app.adblock

import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private fun engineWith(vararg lines: Pair<String, FilterSource>): FilterEngine {
        val engine = FilterEngine()
        for ((line, source) in lines) {
            engine.addRule(FilterListParser.parseNetworkRule(line, source))
        }
        return engine
    }

    @Test
    fun `blocks a request matching a subscribed domain rule`() {
        val engine = engineWith("||doubleclick.net^" to FilterSource.SUBSCRIBED)
        val decision = engine.classify(
            url = "https://ads.doubleclick.net/pixel",
            requestHostname = "ads.doubleclick.net",
            pageHostname = "news.example.com",
            resourceType = ResourceType.IMAGE
        )
        assertTrue(decision is FilterDecision.Block)
    }

    @Test
    fun `allows a request with no matching rule`() {
        val engine = engineWith("||doubleclick.net^" to FilterSource.SUBSCRIBED)
        val decision = engine.classify(
            url = "https://news.example.com/article.html",
            requestHostname = "news.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.DOCUMENT
        )
        assertTrue(decision is FilterDecision.Allow)
    }

    @Test
    fun `manual exception overrides a subscribed block rule regardless of order`() {
        val engine = engineWith(
            "||cdn.example.com^" to FilterSource.SUBSCRIBED,
            "@@||cdn.example.com^" to FilterSource.MANUAL
        )
        val decision = engine.classify(
            url = "https://cdn.example.com/lib.js",
            requestHostname = "cdn.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.SCRIPT
        )
        assertTrue(decision is FilterDecision.Allow)
    }

    @Test
    fun `third-party option only blocks cross-site requests`() {
        val engine = engineWith("||tracker.com^\$third-party" to FilterSource.SUBSCRIBED)

        val thirdParty = engine.classify(
            url = "https://tracker.com/pixel",
            requestHostname = "tracker.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.IMAGE
        )
        assertTrue(thirdParty is FilterDecision.Block)

        val firstParty = engine.classify(
            url = "https://tracker.com/pixel",
            requestHostname = "tracker.com",
            pageHostname = "tracker.com",
            resourceType = ResourceType.IMAGE
        )
        assertTrue(firstParty is FilterDecision.Allow)
    }

    @Test
    fun `resource-type option restricts which requests a rule matches`() {
        val engine = engineWith("||cdn.example.com^\$script" to FilterSource.SUBSCRIBED)

        val script = engine.classify(
            url = "https://cdn.example.com/lib.js",
            requestHostname = "cdn.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.SCRIPT
        )
        assertTrue(script is FilterDecision.Block)

        val image = engine.classify(
            url = "https://cdn.example.com/logo.png",
            requestHostname = "cdn.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.IMAGE
        )
        assertTrue(image is FilterDecision.Allow)
    }

    @Test
    fun `negated resource-type option excludes that type while still matching others`() {
        // Reproduces a real rule shape from EasyList: applies to everything except the
        // negated types. Before the fix, "~image" was silently ignored, so this rule
        // incorrectly matched IMAGE requests too.
        val engine = engineWith("://ads.\$~image,~xmlhttprequest" to FilterSource.SUBSCRIBED)

        val image = engine.classify(
            url = "https://ads.example.com/user_sync.html",
            requestHostname = "ads.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.IMAGE
        )
        assertTrue(image is FilterDecision.Allow)

        val script = engine.classify(
            url = "https://ads.example.com/tag.js",
            requestHostname = "ads.example.com",
            pageHostname = "news.example.com",
            resourceType = ResourceType.SCRIPT
        )
        assertTrue(script is FilterDecision.Block)
    }

    @Test
    fun `domain-indexed lookup finds rules registered under a parent domain`() {
        val engine = engineWith("||example.com^" to FilterSource.SUBSCRIBED)
        val decision = engine.classify(
            url = "https://deeply.nested.sub.example.com/x",
            requestHostname = "deeply.nested.sub.example.com",
            pageHostname = "other.com",
            resourceType = ResourceType.OTHER
        )
        assertTrue(decision is FilterDecision.Block)
    }

    @Test
    fun `short literal index retains wildcard matching and exception priority`() {
        val engine = engineWith(
            "abc*def" to FilterSource.SUBSCRIBED,
            "@@abc*safe" to FilterSource.SUBSCRIBED
        )
        fun decision(url: String) = engine.classify(url, "example.com", "example.com", ResourceType.OTHER)

        assertTrue(decision("https://example.com/abc-long-def") is FilterDecision.Block)
        assertTrue(decision("https://example.com/abc-long-safe-def") is FilterDecision.Allow)
        assertTrue(decision("https://example.com/def-long-abc") is FilterDecision.Allow)
        // Non-ASCII URLs use the full matcher rather than the packed ASCII index.
        assertTrue(decision("https://example.com/café/abc-long-def") is FilterDecision.Block)
    }

    @Test
    fun `many short unindexed rules do not prevent a list from loading`() {
        val engine = FilterEngine()
        repeat(2_100) { index ->
            engine.addRule(FilterListParser.parseNetworkRule("xy\$domain=unrelated$index.example", FilterSource.SUBSCRIBED))
        }
        engine.addRule(FilterListParser.parseNetworkRule("xy\$domain=site.example", FilterSource.SUBSCRIBED))

        assertTrue(engine.classify("https://site.example/xy", "site.example", "site.example", ResourceType.OTHER) is FilterDecision.Block)
    }
}
