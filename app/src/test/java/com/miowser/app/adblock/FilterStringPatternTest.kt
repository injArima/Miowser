package com.miowser.app.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStringPatternTest {
    @Test fun `literal anchors and final separators use string matching`() {
        listOf(
            "ad.js" to "https://cdn.example/AD.JS?x=1",
            "|https://cdn.example/" to "HTTPS://CDN.EXAMPLE/file",
            "ad.js|" to "https://cdn.example/AD.JS",
            "|https://cdn.example/ad.js|" to "https://CDN.example/AD.JS",
            "ads^" to "https://cdn.example/ads?x=1",
            "ads^|" to "https://cdn.example/ads/",
            "||cdn.example/ads" to "https://a.b.CDN.EXAMPLE/ADS.js",
            "||cdn.example/ads^" to "https://cdn.example/ads?x=1",
            "||cdn.example/ads^|" to "https://cdn.example/ads/",
            "||cdn.example/ads|" to "https://cdn.example/ads",
            "foo|bar" to "https://cdn.example/foo|bar"
        ).forEach { (pattern, url) ->
            val rule = rule(pattern)
            assertNotEquals("No fast path for $pattern", 0, rule.stringPattern)
            assertEquals("Did not use string path for $pattern", FilterStringPattern.MATCH,
                FilterStringPattern.match(rule.regexPattern!!, rule.stringPattern, url))
            assertEquivalent(pattern, listOf(url, "$url-extra", "https://safe.example/ordinary"))
        }
    }

    @Test fun `complex patterns and non ASCII literals retain regex fallback`() {
        listOf("ads*banner", "foo^bar", "||cdn.*/ads", "ads^^", "*", "|", "||", "a\\Ebc", "éads", "İads").forEach { pattern ->
            val expression = AdblockPatternCompiler.expression(pattern)
            assertEquals("Overbroad classification of $pattern", 0, FilterStringPattern.describe(expression))
            assertEquivalent(pattern, listOf("https://cdn.example/ads/banner", "a\\Ebc", "https://example/éads"))
        }
        // Only recognize the compiler's complete quoted shape, never a prefix of an expression.
        listOf("\\Qads\\E.*", "(?:\\Qads\\E)", "\\Qads\\E|other", "\\Qads\\E\$junk").forEach {
            assertEquals(0, FilterStringPattern.describe(it))
        }
    }

    @Test fun `separator exclusions and repeated occurrences match regex`() {
        val suffixes = listOf("", "?", "/", ":", "=", "&", "#", "!", "a", "Z", "0", "_", ".", "%", "-", "é", "K", "ſ", "İ", "\n", "\r\n")
        listOf("ads^", "ads^|", "|ads^", "|ads^|", "||ads^", "||ads^|").forEach { pattern ->
            assertEquivalent(pattern, suffixes.flatMap { suffix -> listOf("ads$suffix", "https://ads$suffix", "https://cdn/ads$suffix", "ads_suffix/ads$suffix") })
        }
    }

    @Test fun `end anchors preserve final line terminator semantics`() {
        listOf("\n", "\r", "\r\n", "\u0085", "\u2028", "\u2029").forEach { terminator ->
            listOf("ads|", "|ads|", "ads^|", "||cdn.example/ads|").forEach { pattern ->
                assertEquivalent(pattern, listOf("ads$terminator", "ads/$terminator", "https://cdn.example/ads$terminator", "ads${terminator}more"))
            }
        }
        val expression = AdblockPatternCompiler.expression("ads|")
        assertEquals(FilterStringPattern.FALLBACK, FilterStringPattern.match(expression, FilterStringPattern.describe(expression), "ads\n"))
    }

    @Test fun `Unicode comparison falls back without changing case equivalences`() {
        val urls = listOf("http://example/adſ", "http://example/K", "http://example/İ", "http://example/ı", "http://example/ß", "https://ſub.example/ads", "Kttp://example/ads", "https://example/😀ads", "https://example/𐐀")
        listOf("ads", "k", "i", "ss", "||example/ads", "ads^", "ads|").forEach { assertEquivalent(it, urls) }
        val expression = AdblockPatternCompiler.expression("ads")
        assertEquals(FilterStringPattern.FALLBACK, FilterStringPattern.match(expression, FilterStringPattern.describe(expression), "adſ"))
    }

    @Test fun `domain anchors preserve scheme label and authority behavior`() {
        val urls = listOf(
            "http://cdn.example/ads", "HTTPS://a.b.CDN.EXAMPLE/ADS", "x+1.-://sub.cdn.example/ads",
            "1http://cdn.example/ads", "://cdn.example/ads", "http:/cdn.example/ads",
            "https://notcdn.example/ads", "https://cdn.example.evil/ads", "https://evil/cdn.example/ads",
            "https://user@cdn.example/ads", "https://cdn.example:443/ads", "https://_sub.cdn.example/ads",
            "https://-sub.cdn.example/ads", "https://.cdn.example/ads", "https://cdn.example/ads?x=1",
            "https://sub.\ncdn.example/ads", "https://K.cdn.example/ads", "", "http://"
        )
        listOf("||cdn.example/ads", "||cdn.example/ads^", "||cdn.example/ads|", "||cdn.example/ads^|", "||cdn.example", "||user@cdn.example/ads").forEach {
            assertEquivalent(it, urls)
        }
    }

    @Test fun `generated pattern and URL corpus agrees with original regex`() {
        val random = Random(1303)
        val pieces = listOf("a", "B", "c", "D", "0", "-", "_", ".", "%", "/", ":", "?", "[", "]", "|", "\\", "é", "K", "ſ", "\n", "\r\n")
        repeat(160) {
            val literal = buildString { repeat(random.nextInt(1, 18)) { append(pieces[random.nextInt(16)]) } }
            val pattern = listOf("", "|", "||")[random.nextInt(3)] + literal + listOf("", "|", "^", "^|")[random.nextInt(4)]
            val urls = mutableListOf(literal, literal.uppercase(), "https://$literal", "https://sub.$literal", "$literal?", "https://example/$literal/", "$literal\n")
            repeat(60) {
                urls += buildString { repeat(random.nextInt(0, 45)) { append(pieces[random.nextInt(pieces.size)]) } }
            }
            assertEquivalent(pattern, urls)
        }
    }

    @Test fun `full predicates and priority are unchanged after optimization and cache restore`() {
        val originals = listOf(
            rule("||ads.example^"), rule("@@||ads.example/allowed"),
            rule("||ads.example/allowed\$script", FilterSource.MANUAL),
            rule("@@||ads.example/manual", FilterSource.MANUAL), rule("||ads.example/manual", FilterSource.MANUAL),
            rule("ads.js^\$script,third-party,domain=site.example|~safe.site.example"),
            rule("tracker|\$~image"), rule("wild*card\$media"), rule("@@tracker\$image")
        )
        val reference = FilterEngine().apply { originals.forEach { addRule(regexOnly(it)) } }
        val actual = FilterEngine().apply { originals.forEach(::addRule); assertTrue(optimizeTokenIndexes()) }
        val restored = restore(actual)
        val urls = listOf("https://ads.example/file", "https://ads.example/allowed.js", "https://ads.example/manual.js", "https://cdn.example/ADS.JS?x=1", "https://cdn.example/tracker", "https://cdn.example/tracker\n", "https://cdn.example/wild/card", "https://cdn.example/adſ.js", "https://cdn.example/ads.js?" + "x".repeat(17_000))
        urls.forEach { url ->
            listOf("site.example", "safe.site.example", "cdn.example").forEach { page ->
                ResourceType.entries.forEach { type ->
                    val expected = reference.classify(url, if ("ads.example" in url) "ads.example" else "cdn.example", page, type)
                    listOf(actual, restored).forEach { engine ->
                        val result = engine.classify(url, if ("ads.example" in url) "ads.example" else "cdn.example", page, type)
                        assertEquals("Decision changed: $url $page $type", signature(expected), signature(result))
                    }
                }
            }
        }
        var restoredFastPaths = 0
        restored.forEachRule { if (it.stringPattern != 0) restoredFastPaths++ }
        assertEquals(originals.count { it.stringPattern != 0 }, restoredFastPaths)
    }

    @Test fun `bundled simple patterns match original regex on positive and negative URLs`() {
        var eligible = 0
        File("src/main/assets/easylist.txt").bufferedReader().use { reader ->
            FilterListParser.scan(reader, FilterSource.SUBSCRIBED, onNetwork = { rule ->
                if (rule.stringPattern != 0) {
                    eligible++
                    val pattern = rule.raw.removePrefix("@@").substringBeforeLast('$', rule.raw.removePrefix("@@"))
                    val literal = pattern.removePrefix("||").removePrefix("|").removeSuffix("|").removeSuffix("^")
                    assertEquivalent(pattern, listOf(literal, literal.uppercase(), "https://$literal", "https://sub.$literal", "https://$literal?x=1", "https://cdn.example/$literal", "https://cdn.example/$literal?x=1", "https://$literal-extra", "https://ordinary.example/file.js"))
                }
            })
        }
        assertTrue("Expected bundled network rules to exercise the string path", eligible > 1_000)
        println("Bundled EasyList: $eligible non-plain-domain rules eligible for string matching (not a device timing)")
    }

    private fun assertEquivalent(pattern: String, urls: List<String>) {
        val expression = AdblockPatternCompiler.expression(pattern)
        val descriptor = FilterStringPattern.describe(expression)
        val reference = AdblockPatternCompiler.compile(pattern)
        urls.forEach { url ->
            val result = FilterStringPattern.match(expression, descriptor, url)
            val actual = if (result == FilterStringPattern.FALLBACK) reference.containsMatchIn(url) else result == FilterStringPattern.MATCH
            assertEquals("Pattern $pattern disagrees for ${url.replace("\n", "\\n").replace("\r", "\\r")}", reference.containsMatchIn(url), actual)
        }
    }

    private fun rule(raw: String, source: FilterSource = FilterSource.SUBSCRIBED) = FilterListParser.parseNetworkRule(raw, source)
    private fun regexOnly(rule: NetworkFilterRule) = rule.regexPattern?.let { rule.copy(regexPattern = "(?:$it)") } ?: rule
    private fun signature(decision: FilterDecision): String = when (decision) {
        FilterDecision.Allow -> "allowed"
        is FilterDecision.Block -> "${decision.rule.source}:${decision.rule.raw}"
    }
    private fun restore(engine: FilterEngine): FilterEngine {
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { CompiledFilterSnapshot.write(it, "p3", engine, emptyMap(), emptyList(), 0) }
        }.toByteArray()
        return DataInputStream(ByteArrayInputStream(bytes)).use { CompiledFilterSnapshot.read(it, "p3")!!.network }
    }
}
