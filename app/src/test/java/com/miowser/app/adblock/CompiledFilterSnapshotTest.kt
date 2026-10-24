package com.miowser.app.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompiledFilterSnapshotTest {
    @Test
    fun `restored buckets preserve priorities options and fallback ordering`() {
        val original = FilterEngine()
        listOf(
            "||ads.example^" to FilterSource.SUBSCRIBED,
            "@@||ads.example/allowed*\$script" to FilterSource.SUBSCRIBED,
            "||ads.example/allowed*\$script" to FilterSource.MANUAL,
            "@@||ads.example/manual*" to FilterSource.MANUAL,
            "||ads.example/manual*" to FilterSource.MANUAL,
            "banner*first\$image,third-party,domain=site.example|~safe.site.example" to FilterSource.SUBSCRIBED,
            "banner*second\$~script" to FilterSource.SUBSCRIBED,
            "abc*def\$image" to FilterSource.SUBSCRIBED,
            "ab*xy\$media" to FilterSource.SUBSCRIBED
        ).forEach { (raw, source) -> original.addRule(FilterListParser.parseNetworkRule(raw, source)) }
        // Several token-table growths and colliding buckets, including two matching tokens per URL.
        repeat(100) { index ->
            val token = "t" + index.toString().padStart(4, '0')
            original.addRule(FilterListParser.parseNetworkRule("${token}suffix*end", FilterSource.SUBSCRIBED))
        }
        val loaded = read(write(original))!!.network
        val paths = listOf(
            "https://ads.example/allowed.js", "https://sub.ads.example/normal.js",
            "https://ads.example/manual.js", "https://cdn.example/banner-first.jpg",
            "https://cdn.example/banner-second.jpg", "https://cdn.example/abc-def.jpg",
            "https://cdn.example/ab-xy.mp4", "https://cdn.example/t0019suffix-end-t0097suffix-end",
            "https://cdn.example/é/t0019suffix-end-t0097suffix-end",
            "https://cdn.example/t0019suffix-end-t0097suffix-end?" + "x".repeat(17_000)
        )
        paths.forEach { url ->
            listOf("site.example", "safe.site.example", "cdn.example").forEach { page ->
                listOf(ResourceType.IMAGE, ResourceType.SCRIPT, ResourceType.MEDIA).forEach { type ->
                    val host = java.net.URI(url).host
                    assertEquals("Decision changed for $url, $page, $type",
                        original.classify(url, host, page, type), loaded.classify(url, host, page, type))
                }
            }
        }
        assertEquals(original.anchoredDomainMasks(), loaded.anchoredDomainMasks())
        // Saving an already restored engine must preserve the same physical token layout.
        assertTrue(write(original).contentEquals(write(loaded)))
    }

    @Test
    fun `empty installed matcher can be restored`() {
        val loaded = read(write(FilterEngine()))!!
        assertEquals(FilterDecision.Allow, loaded.network.classify(
            "https://site.example/", "site.example", "site.example", ResourceType.DOCUMENT))
    }

    @Test
    fun `old snapshot version is invalidated`() {
        listOf(1, 2, 3).forEach { version ->
            val old = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { it.writeInt(0x4d574631); it.writeInt(version) }
            }.toByteArray()
            assertNull(read(old))
        }
    }

    @Test
    fun `truncated changed and extended caches are rejected`() {
        val original = FilterEngine().apply {
            addRule(FilterListParser.parseNetworkRule("||ads.example^", FilterSource.SUBSCRIBED))
        }
        val bytes = write(original)
        listOf(1, 8, bytes.size / 2).forEach { removed ->
            assertTrue("Truncated cache accepted", runCatching { read(bytes.copyOf(bytes.size - removed)) }.isFailure)
        }
        // A same-length edit remains structurally readable; checksum must still reject it.
        val edited = bytes.clone()
        val pattern = "||ads.example^".toByteArray(Charsets.US_ASCII)
        val offset = (0..edited.size - pattern.size).first { start ->
            pattern.indices.all { edited[start + it] == pattern[it] }
        }
        edited[offset + 2] = 'b'.code.toByte()
        assertEquals("Filter snapshot checksum mismatch", runCatching { read(edited) }.exceptionOrNull()?.message)
        assertTrue("Trailing data accepted", runCatching { read(bytes + byteArrayOf(0)) }.isFailure)
    }

    private fun write(network: FilterEngine): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use {
            CompiledFilterSnapshot.write(it, "test", network, emptyMap(), emptyList(), 0)
        }
    }.toByteArray()

    private fun read(bytes: ByteArray): CompiledFilterSnapshot.Loaded? =
        DataInputStream(ByteArrayInputStream(bytes)).use { CompiledFilterSnapshot.read(it, "test") }

    @Test
    fun `round trip preserves network decisions and cosmetic rules`() {
        val engine = FilterEngine()
        listOf(
            "||ads.example^" to FilterSource.SUBSCRIBED,
            "@@||ads.example/allowed.js\$script" to FilterSource.SUBSCRIBED,
            "||cdn.example/banner*\$image,third-party,domain=site.example" to FilterSource.SUBSCRIBED,
            "tracker.js\$script" to FilterSource.SUBSCRIBED,
            "||manual.example^" to FilterSource.MANUAL
        ).forEach { (raw, source) -> engine.addRule(FilterListParser.parseNetworkRule(raw, source)) }
        val generic = mutableListOf(
            CosmeticRule(null, ".sponsored", false, FilterSource.SUBSCRIBED, listOf("safe.example"))
        )
        val domains = mutableMapOf("site.example" to mutableListOf(
            CosmeticRule("site.example", ".banner", false, FilterSource.SUBSCRIBED),
            CosmeticRule("site.example", ".banner", true, FilterSource.MANUAL)
        ))
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { CompiledFilterSnapshot.write(it, "fingerprint", engine, domains, generic, 7) }
        }.toByteArray()
        assertNull(CompiledFilterSnapshot.read(DataInputStream(ByteArrayInputStream(bytes)), "changed"))
        val loaded = CompiledFilterSnapshot.read(DataInputStream(ByteArrayInputStream(bytes)), "fingerprint")!!
        assertEquals(7, loaded.skippedSources)
        assertEquals(generic, loaded.generic)
        assertEquals(domains, loaded.domains)
        val cases = listOf(
            Triple("https://ads.example/banner.js", "site.example", ResourceType.SCRIPT),
            Triple("https://ads.example/allowed.js", "site.example", ResourceType.SCRIPT),
            Triple("https://cdn.example/banner-1.jpg", "site.example", ResourceType.IMAGE),
            Triple("https://cdn.example/banner-1.jpg", "cdn.example", ResourceType.IMAGE),
            Triple("https://other.example/tracker.js", "site.example", ResourceType.SCRIPT),
            Triple("https://manual.example/video.mp4", "site.example", ResourceType.MEDIA)
        )
        cases.forEach { (url, page, type) ->
            val host = java.net.URI(url).host
            fun result(matcher: FilterEngine): String = when (val decision = matcher.classify(url, host, page, type)) {
                is FilterDecision.Block -> decision.rule.raw
                FilterDecision.Allow -> "allowed"
            }
            assertEquals("Decision changed for $url", result(engine), result(loaded.network))
        }
    }
}
