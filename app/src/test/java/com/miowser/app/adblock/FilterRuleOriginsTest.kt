package com.miowser.app.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRuleOriginsTest {
    private class Fixture {
        val origins = FilterRuleOrigins()
        val network = FilterEngine()
        val accumulator = FilterRuleAccumulator(network, origins)

        fun add(id: String, text: String, source: FilterSource = FilterSource.SUBSCRIBED) {
            val group = origins.register(FilterRuleSource(id, "Name $id", if (source == FilterSource.SUBSCRIBED) "https://lists.example/$id.txt" else null))
            text.reader().buffered().use { reader ->
                FilterListParser.scan(reader, source,
                    acceptLine = { accumulator.accept(it, source, group) },
                    onNetwork = { accumulator.add(it, group) })
            }
        }

        fun blockingIds(url: String = "https://blocked.example/path"): List<String> =
            blockingSources(network, origins, url).map { it.id }

        fun count(): Int { var count = 0; network.forEachRule { count++ }; return count }
    }

    @Test fun `duplicates retain all list sources without retaining duplicate rules`() {
        val fixture = Fixture()
        fixture.add("first", "||blocked.example^")
        fixture.add("second", "||blocked.example^")
        fixture.add("hosts", "0.0.0.0 blocked.example")
        assertEquals(1, fixture.count())
        assertEquals(listOf("first", "second", "hosts"), fixture.blockingIds())
    }

    @Test fun `different matching rules include their lists and exclude non matching constraints`() {
        val fixture = Fixture()
        fixture.add("domain", "||blocked.example^")
        fixture.add("path", "||blocked.example/path")
        fixture.add("other-path", "||blocked.example/other")
        fixture.add("image-only", "||blocked.example^\$image")
        assertEquals(listOf("domain", "path"), fixture.blockingIds())
    }

    @Test fun `custom rules keep their priority and exceptions do not create blocked reports`() {
        val fixture = Fixture()
        fixture.add("subscribed-allow", "@@||blocked.example^")
        fixture.add("subscribed-block", "||blocked.example^")
        fixture.add("custom", "||blocked.example^", FilterSource.MANUAL)
        assertEquals(3, fixture.count())
        assertEquals(listOf("custom"), fixture.blockingIds())
        fixture.add("custom", "@@||blocked.example^", FilterSource.MANUAL)
        assertTrue(fixture.blockingIds().isEmpty())
    }

    @Test fun `source groups are not limited to a machine word`() {
        val fixture = Fixture()
        repeat(80) { fixture.add("list$it", "||blocked.example^") }
        assertEquals(1, fixture.count())
        assertEquals((0 until 80).map { "list$it" }, fixture.blockingIds())
    }

    @Test fun `optimized cache preserves merged sources and matching list attribution`() {
        val fixture = Fixture()
        fixture.add("first", "sharedprefixassets*requiredsuffix")
        fixture.add("second", "sharedprefixassets*requiredsuffix")
        fixture.add("other-rule", "sharedprefixassets*another-required")
        fixture.accumulator.release()
        fixture.origins.freeze()
        assertTrue(fixture.network.optimizeTokenIndexes())
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                CompiledFilterSnapshot.write(output, "sources", fixture.network, emptyMap(), emptyList(), 0, fixture.origins)
            }
        }.toByteArray()
        val loaded = DataInputStream(ByteArrayInputStream(bytes)).use { CompiledFilterSnapshot.read(it, "sources")!! }
        val url = "https://cdn.example/sharedprefixassets/requiredsuffix"
        assertEquals(listOf("first", "second"), blockingSources(loaded.network, loaded.origins, url).map { it.id })
        assertEquals(fixture.blockingIds(url), blockingSources(loaded.network, loaded.origins, url).map { it.id })
    }

    @Test fun `invalid cached provenance is rejected`() {
        val network = FilterEngine().apply {
            addRule(FilterListParser.parseNetworkRule("||blocked.example^", FilterSource.SUBSCRIBED).also { it.originGroup = 9 })
        }
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { CompiledFilterSnapshot.write(it, "sources", network, emptyMap(), emptyList(), 0) }
        }.toByteArray()
        val error = runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { CompiledFilterSnapshot.read(it, "sources") }
        }.exceptionOrNull()
        assertEquals("Invalid rule source group", error?.message)
    }

    companion object {
        private fun blockingSources(network: FilterEngine, origins: FilterRuleOrigins, url: String): List<FilterRuleSource> {
            val host = java.net.URI(url).host
            val decision = network.classify(url, host, host, ResourceType.DOCUMENT) as? FilterDecision.Block ?: return emptyList()
            return origins.sourcesFor(network.blockingOriginGroups(url, host, host, ResourceType.DOCUMENT, decision))
        }
    }
}
