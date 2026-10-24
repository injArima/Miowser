package com.miowser.app.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTokenOptimizationTest {
    @Test fun `rare required fragments avoid checking a shared prefix bucket`() {
        val engine = FilterEngine()
        repeat(2_000) { index ->
            engine.addRule(rule("sharedprefixassets*unique_${index.toString().padStart(5, '0')}*banner"))
        }
        val unrelated = "https://cdn.example/sharedprefixassets/ordinary-file.js"
        val before = engine.urlRuleCandidateCount(unrelated)
        assertEquals(2_000, before)
        assertTrue(engine.optimizeTokenIndexes())
        val after = engine.urlRuleCandidateCount(unrelated)
        assertTrue("Expected fewer full-predicate candidates: $before -> $after", after <= before / 10)
        assertEquals(FilterDecision.Allow, classify(engine, unrelated))
        listOf(0, 19, 425, 1_999).forEach { index ->
            assertTrue(classify(engine, "https://cdn.example/sharedprefixassets/unique_${index.toString().padStart(5, '0')}/banner")
                is FilterDecision.Block)
        }
        println("Shared-prefix regression: $before -> $after URL-rule candidates (not a device timing)")
    }

    @Test fun `literal windows do not span wildcards separators or options`() {
        val raw = "@@|https://example.test/shared*xyz^aBcd\$script,domain=not-a-required-token.example"
        val values = mutableSetOf<Long>()
        FilterTokenSelection.forEach(raw, 5, values::add)
        assertTrue(packFilterToken("share") in values)
        assertTrue(packFilterToken("https") in values)
        assertFalse(packFilterToken("dxyza") in values)
        assertFalse(packFilterToken("not-a") in values)
        assertEquals(3, FilterTokenSelection.width("abc*defg\$script"))
        assertEquals(0, FilterTokenSelection.width("ab*xy\$script"))
    }

    @Test fun `optimization preserves decisions priority constraints and non ASCII fallback`() {
        val before = FilterEngine()
        val after = FilterEngine()
        listOf(
            "||ads.example^" to FilterSource.SUBSCRIBED,
            "@@sharedprefixassets*allowed" to FilterSource.SUBSCRIBED,
            "sharedprefixassets*allowed\$script" to FilterSource.MANUAL,
            "@@sharedprefixassets*manual" to FilterSource.MANUAL,
            "sharedprefixassets*manual" to FilterSource.MANUAL,
            "SHAREDPREFIXASSETS*alpha*omega\$script,third-party,domain=site.example|~safe.site.example" to FilterSource.SUBSCRIBED,
            "abc*def\$image" to FilterSource.SUBSCRIBED,
            "xy*zz\$media" to FilterSource.SUBSCRIBED,
            "foo-bar^baz\$~script" to FilterSource.SUBSCRIBED,
            "commonprefix*file%2Fname" to FilterSource.SUBSCRIBED
        ).forEach { (raw, source) ->
            val rule = FilterListParser.parseNetworkRule(raw, source)
            before.addRule(rule); after.addRule(rule)
        }
        repeat(300) { index ->
            val rule = rule("sharedprefixassets*unique_${index.toString().padStart(5, '0')}*banner")
            before.addRule(rule); after.addRule(rule)
        }
        assertTrue(after.optimizeTokenIndexes())
        val urls = listOf(
            "https://ads.example/file.js", "https://cdn.example/sharedprefixassets/allowed",
            "https://cdn.example/sharedprefixassets/manual", "https://cdn.example/SHAREDPREFIXASSETS/alpha/omega",
            "https://cdn.example/sharedprefixassets/omega/alpha", "https://cdn.example/abc-def",
            "https://cdn.example/xy-zz", "https://cdn.example/foo-bar/baz",
            "https://cdn.example/commonprefix/file%2fname", "https://cdn.example/sharedprefixassets/unique_00299/banner",
            "https://cdn.example/é/sharedprefixassets/unique_00299/banner",
            "https://cdn.example/sharedprefixassets/unique_00299/banner?" + "x".repeat(17_000)
        )
        urls.forEach { url ->
            listOf("site.example", "safe.site.example", "cdn.example").forEach { page ->
                listOf(ResourceType.SCRIPT, ResourceType.IMAGE, ResourceType.MEDIA).forEach { type ->
                    assertSameEffect(before, after, url, page, type)
                }
            }
        }
        assertEquals(before.anchoredDomainMasks(), after.anchoredDomainMasks())
        assertTrue(classify(after, urls[1]) is FilterDecision.Block) // manual block beats subscribed exception
        assertEquals(FilterDecision.Allow, classify(after, urls[2])) // manual exception beats manual block
        assertEquals(FilterDecision.Allow, classify(after, urls[4])) // token hit still needs the full predicate
    }

    @Test fun `optimized choices survive snapshots and adding another rule`() {
        val engine = FilterEngine()
        repeat(300) { index -> engine.addRule(rule("sharedprefixassets*unique_${index.toString().padStart(5, '0')}*banner")) }
        assertTrue(engine.optimizeTokenIndexes())
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { CompiledFilterSnapshot.write(it, "optimized", engine, emptyMap(), emptyList(), 0) }
        val restored = DataInputStream(ByteArrayInputStream(stream.toByteArray())).use {
            CompiledFilterSnapshot.read(it, "optimized")!!.network
        }
        val ordinary = "https://cdn.example/sharedprefixassets/ordinary"
        assertEquals(engine.urlRuleCandidateCount(ordinary), restored.urlRuleCandidateCount(ordinary))
        val hit = "https://cdn.example/sharedprefixassets/unique_00299/banner"
        assertEquals(classify(engine, hit), classify(restored, hit))
        restored.addRule(rule("anotherprefix*added-rule"))
        assertTrue(classify(restored, "https://cdn.example/anotherprefix/added-rule") is FilterDecision.Block)
        assertTrue(restored.optimizeTokenIndexes())
        assertTrue(classify(restored, hit) is FilterDecision.Block)
    }

    @Test fun `insufficient optimization headroom retains compatible buckets`() {
        val engine = FilterEngine().apply { addRule(rule("sharedprefixassets*requiredsuffix")) }
        val url = "https://cdn.example/sharedprefixassets/requiredsuffix"
        val before = classify(engine, url)
        assertFalse(engine.optimizeTokenIndexes(FilterMemoryGuard(reserveBytes = 20, availableBytes = { 30 })))
        assertEquals(before, classify(engine, url))
    }

    @Test fun `bundled list keeps blocking and allowing the same request corpus`() {
        val before = FilterEngine()
        val after = FilterEngine()
        File("src/main/assets/easylist.txt").bufferedReader().use {
            FilterListParser.scan(it, FilterSource.SUBSCRIBED, onNetwork = { rule ->
                before.addRule(rule); after.addRule(rule)
            })
        }
        assertTrue(after.optimizeTokenIndexes())
        val urls = listOf(
            "https://000491b06a.com/ad.js", "https://cdn.example/assets/app.js",
            "https://cdn.example/advertisement-banner.png", "https://cdn.example/analytics/beacon",
            "https://www.qorno.com/", "https://www.qorno.com/static/app.js",
            "https://www.qorno.com/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1",
            "https://cdn.example/é/advertisement", "https://cdn.example/image?" + "x".repeat(17_000)
        )
        urls.forEach { url ->
            listOf(ResourceType.DOCUMENT, ResourceType.SCRIPT, ResourceType.IMAGE).forEach { type ->
                assertSameEffect(before, after, url, "site.example", type)
            }
        }
        assertTrue(classify(after, urls[0]) is FilterDecision.Block)
    }

    private fun rule(raw: String) = FilterListParser.parseNetworkRule(raw, FilterSource.SUBSCRIBED)

    private fun classify(engine: FilterEngine, url: String): FilterDecision =
        engine.classify(url, java.net.URI(url).host, "site.example", ResourceType.SCRIPT)

    private fun assertSameEffect(before: FilterEngine, after: FilterEngine, url: String, page: String, type: ResourceType) {
        val host = java.net.URI(url).host
        val expected = before.classify(url, host, page, type)
        val actual = after.classify(url, host, page, type)
        assertEquals("Allow/block changed for $url, $page, $type", expected is FilterDecision.Allow, actual is FilterDecision.Allow)
        if (expected is FilterDecision.Block && actual is FilterDecision.Block) {
            assertEquals("Rule source priority changed", expected.rule.source, actual.rule.source)
        }
    }
}
