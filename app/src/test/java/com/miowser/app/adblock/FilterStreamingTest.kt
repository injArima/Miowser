package com.miowser.app.adblock

import java.io.Reader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStreamingTest {
    @Test
    fun `optimized URL token tables can reload alongside the previous generation`() {
        val original = FilterEngine()
        val memory = FilterMemoryGuard()
        repeat(30_000) { index ->
            if (index % 256 == 0) memory.check()
            original.addRule(FilterListParser.parseNetworkRule(
                "sharedprefixassets*unique_${index.toString().padStart(5, '0')}*banner\$script", FilterSource.SUBSCRIBED))
        }
        assertTrue(original.optimizeTokenIndexes())
        val file = File.createTempFile("optimized-filter-memory", ".bin")
        try {
            DataOutputStream(file.outputStream().buffered()).use {
                CompiledFilterSnapshot.write(it, "optimized", original, emptyMap(), emptyList(), 0)
            }
            val loaded = DataInputStream(file.inputStream().buffered()).use {
                CompiledFilterSnapshot.read(it, "optimized")!!.network
            }
            var count = 0
            loaded.forEachRule { count++ }
            assertEquals(30_000, count)
            val url = "https://cdn.example/sharedprefixassets/unique_29999/banner"
            assertEquals(original.classify(url, "cdn.example", "site.example", ResourceType.SCRIPT),
                loaded.classify(url, "cdn.example", "site.example", ResourceType.SCRIPT))
            assertEquals(original.urlRuleCandidateCount("https://cdn.example/sharedprefixassets/ordinary"),
                loaded.urlRuleCandidateCount("https://cdn.example/sharedprefixassets/ordinary"))
        } finally { file.delete() }
    }

    @Test
    fun `two generations of sixty thousand URL pattern rules can coexist`() {
        val original = FilterEngine()
        val memory = FilterMemoryGuard()
        repeat(60_000) { index ->
            if (index % 256 == 0) memory.check()
            original.addRule(FilterListParser.parseNetworkRule(
                "||ads$index.example/path/*${'$'}script,image,third-party", FilterSource.SUBSCRIBED))
        }
        val file = File.createTempFile("filter-memory-regression", ".bin")
        try {
            DataOutputStream(file.outputStream().buffered()).use {
                CompiledFilterSnapshot.write(it, "test", original, emptyMap(), emptyList(), 0)
            }
            val replacement = DataInputStream(file.inputStream().buffered()).use {
                CompiledFilterSnapshot.read(it, "test")!!
            }
            var count = 0
            replacement.network.forEachRule { count++ }
            assertEquals(60_000, count)
            listOf(original, replacement.network).forEach { engine ->
                assertTrue(engine.classify("https://ads59999.example/path/banner", "ads59999.example",
                    "site.example", ResourceType.SCRIPT) is FilterDecision.Block)
                assertEquals(FilterDecision.Allow, engine.classify("https://ads59999.example/path/banner",
                    "ads59999.example", "site.example", ResourceType.MEDIA))
            }
        } finally { file.delete() }
    }

    @Test
    fun `streaming validation preserves counts and supported matching semantics`() {
        val text = """
            ! notes
            0.0.0.0 ads.example tracker.example
            ||ads.example^
            @@||ads.example/allowed.js${'$'}script
            example.com,~safe.example.com##.advert
            ##.banner
            /unsupported.*/
            ||unsupported.example^${'$'}redirect=noopjs
        """.trimIndent()
        val collected = FilterListParser.parse(text, FilterSource.SUBSCRIBED)
        val network = mutableListOf<NetworkFilterRule>()
        val cosmetic = mutableListOf<CosmeticRule>()
        val counts = text.reader().buffered().use {
            FilterListParser.scan(it, FilterSource.SUBSCRIBED, HashSet<String>()::add, network::add, cosmetic::add)
        }
        assertEquals(collected.networkRules, network)
        assertEquals(collected.cosmeticRules, cosmetic)
        assertEquals(collected.networkRules.size, counts.networkRules)
        assertEquals(collected.cosmeticRules.size, counts.cosmeticRules)
        assertEquals(collected.skippedRegex, counts.skippedRegex)
        assertEquals(collected.skippedUnsupported, counts.skippedUnsupported)
    }

    @Test
    fun `overlapping sources reject duplicates before constructing and delivering rules`() {
        val seen = HashSet<String>()
        var delivered = 0
        repeat(2) {
            "||ads.example^\nexample.com##.ad".reader().buffered().use { reader ->
                FilterListParser.scan(reader, FilterSource.SUBSCRIBED, seen::add,
                    onNetwork = { delivered++ }, onCosmetic = { delivered++ })
            }
        }
        assertEquals(2, delivered)
    }

    @Test
    fun `giant malformed line is skipped without consuming the valid next rule`() {
        // The reader generates the oversized line in chunks, so the test does not create it in memory.
        val oversized = RepeatingReader("x", 2 * 1024 * 1024)
        val tail = "\r\n||ads.example^".reader()
        val joined = object : Reader() {
            var firstDone = false
            override fun read(target: CharArray, offset: Int, length: Int): Int {
                if (!firstDone) {
                    val count = oversized.read(target, offset, length)
                    if (count >= 0) return count
                    firstDone = true
                }
                return tail.read(target, offset, length)
            }
            override fun close() { oversized.close(); tail.close() }
        }
        val result = joined.buffered().use { FilterListParser.scan(it, FilterSource.SUBSCRIBED) }
        assertEquals(1, result.skippedMalformed)
        assertEquals(1, result.networkRules)
    }

    @Test
    fun `large validation stream counts rules without retaining a parsed list`() {
        RepeatingReader("||ads.example/path/*${'$'}script\n", 100_000).buffered().use {
            assertEquals(100_000, FilterListParser.scan(it, FilterSource.SUBSCRIBED).networkRules)
        }
    }

    @Test
    fun `pattern cache eviction preserves case insensitive wildcard matching`() {
        val first = FilterListParser.parseNetworkRule("||ads.example/path/*${'$'}script", FilterSource.SUBSCRIBED)
        repeat(600) { index ->
            val rule = FilterListParser.parseNetworkRule("||ads.example/path$index/*", FilterSource.SUBSCRIBED)
            assertTrue(rule.matches("https://ads.example/path$index/banner", "ads.example", true,
                ResourceType.SCRIPT, "site.example"))
        }
        assertTrue(first.matches("https://ADS.EXAMPLE/PATH/banner", "ads.example", true,
            ResourceType.SCRIPT, "site.example"))
        assertFalse(first.matches("https://ads.example/path/banner", "ads.example", true,
            ResourceType.IMAGE, "site.example"))
    }

    private class RepeatingReader(private val text: String, repetitions: Int) : Reader() {
        private var remaining = text.length.toLong() * repetitions
        private var position = 0
        override fun read(target: CharArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            repeat(count) { target[offset + it] = text[position]; position = (position + 1) % text.length }
            remaining -= count
            return count
        }
        override fun close() = Unit
    }
}
