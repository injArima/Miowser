package com.miowser.app.adblock

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

class P2FilterComparisonTest {
    private data class Request(val url: String, val host: String, val page: String, val type: ResourceType)
    private fun corpus(): List<Request> {
        val types = ResourceType.entries
        val random = java.util.Random(7)
        return (0 until 160).map { i ->
            val tail = if (i % 7 == 0) buildString { repeat(2048) { append(('a'.code + random.nextInt(26)).toChar()) } } else "id=$i"
            val path = when (i % 5) { 0 -> "advertisement-123456"; 1 -> "allowed-banner"; 2 -> "asset.js"; 3 -> "metrics-beacon"; else -> "page.html" }
            val host = if (i % 9 == 0) "ads.tracker.org" else "cdn.example.org"
            Request("https://$host/$path?$tail", host, "news.example.org", types[i % types.size])
        } + listOf(Request("https://cdn.example.org/café?" + "x".repeat(17_000), "cdn.example.org", "news.example.org", ResourceType.SCRIPT))
    }
    private fun rules() = FilterListParser.parse(File("src/main/assets/easylist.txt").readText(), FilterSource.SUBSCRIBED).networkRules +
        listOf("advertisement-123456", "metrics-beacon", "@@allowed-banner").map { FilterListParser.parseNetworkRule(it, FilterSource.SUBSCRIBED) } +
        listOf("@@||ads.tracker.org^", "manual-block").map { FilterListParser.parseNetworkRule(it, FilterSource.MANUAL) }
    @Test fun packedIndexPreservesCorpusDecisionsAndReportsComparableDesktopCosts() {
        val before = P2BaselineFilterEngine(); val after = FilterEngine()
        rules().forEach { before.addRule(it); after.addRule(it) }
        val requests = corpus()
        fun old(request: Request) = before.classify(request.url, request.host, request.page, request.type)
        fun candidate(request: Request) = after.classify(request.url, request.host, request.page, request.type)
        requests.forEach { request ->
            val a = old(request); val b = candidate(request)
            assertEquals(request.url, a is FilterDecision.Allow, b is FilterDecision.Allow)
            if (a is FilterDecision.Block && b is FilterDecision.Block) assertEquals(a.rule.source, b.rule.source)
        }
        // Warm both algorithms and interleave timed batches to reduce JVM/order bias.
        val measured = requests.filter { it.url.all { c -> c.code < 128 } && it.url.length <= 16_384 }
        repeat(8) { measured.forEach { old(it); candidate(it) } }
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val samples = mutableListOf<String>()
        fun measure(label: String, classify: (Request) -> FilterDecision) {
            val bytes = bean.getThreadAllocatedBytes(thread); val cpu = bean.currentThreadCpuTime; val elapsed = System.nanoTime()
            var blocks = 0
            repeat(128) { measured.forEach { if (classify(it) is FilterDecision.Block) blocks++ } }
            val nanos = bean.currentThreadCpuTime - cpu
            samples += "$label\t$nanos\t${System.nanoTime() - elapsed}\t${bean.getThreadAllocatedBytes(thread) - bytes}\t${measured.size * 128}\t$blocks"
        }
        repeat(10) { round ->
            if (round % 2 == 0) { measure("before", ::old); measure("after", ::candidate) }
            else { measure("after", ::candidate); measure("before", ::old) }
        }
        File("../.local/optimization/p2-filter-measurements.tsv").apply { parentFile?.mkdirs() }
            .writeText("implementation\tcpu_ns\telapsed_ns\tallocated_bytes\trequests\tblocked\n" + samples.joinToString("\n"))
    }
    @Test fun tokenHashCollisionsStillRequireExactKeysAndFullRuleMatches() {
        val index = FilterTokenIndex()
        val first = FilterListParser.parseNetworkRule("abcde-path", FilterSource.SUBSCRIBED)
        val token = packFilterToken("abcde")
        var other = token + 1
        while (tokenSlot(other, 15) != tokenSlot(token, 15)) other++
        index.add(token, first)
        assertNull(index.get(other)); assertEquals(first, index.get(token)!!.single())
        val engine = FilterEngine(); engine.addRule(first)
        assertTrue(engine.classify("https://example.org/abcde-other", "example.org", "example.org", ResourceType.SCRIPT) is FilterDecision.Allow)
    }
}
