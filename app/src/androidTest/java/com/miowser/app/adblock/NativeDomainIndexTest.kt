package com.miowser.app.adblock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeDomainIndexTest {
    @Test fun mappedNativeCandidatesPreserveRuleDecisions() {
        assertTrue("Native library must be packaged in the APK", NativeDomainLookup.available)
        val engine = FilterEngine()
        listOf(
            "||tracker.example^" to FilterSource.SUBSCRIBED,
            "@@||tracker.example^\$domain=news.example.net" to FilterSource.MANUAL,
            "@@||allowed.example^" to FilterSource.MANUAL,
            "||example.org^\$script" to FilterSource.SUBSCRIBED,
            "abc*def" to FilterSource.SUBSCRIBED
        ).forEach { (raw, source) -> engine.addRule(FilterListParser.parseNetworkRule(raw, source)) }
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "native-domain-index-test")
        val index = MappedDomainIndex.openOrBuild(folder, "c".repeat(64), engine)
        assertNotNull(index)
        assertEquals(9, index!!.candidateMask("ads.tracker.example"))
        assertEquals(1, index.candidateMask("sub.allowed.example"))
        assertEquals(8, index.candidateMask("www.example.org"))
        assertEquals(0, index.candidateMask("unrelated.test"))

        val requests = listOf(
            Triple("https://ads.tracker.example/pixel", "ads.tracker.example", ResourceType.IMAGE),
            Triple("https://sub.allowed.example/ad.js", "sub.allowed.example", ResourceType.SCRIPT),
            Triple("https://www.example.org/ad.js", "www.example.org", ResourceType.SCRIPT),
            Triple("https://www.example.org/ad.png", "www.example.org", ResourceType.IMAGE),
            Triple("https://unrelated.test/abc-long-def", "unrelated.test", ResourceType.OTHER),
            Triple("https://unrelated.test/normal", "unrelated.test", ResourceType.OTHER)
        )
        for ((url, host, type) in requests) {
            val original = engine.classify(url, host, "news.example.net", type)
            val indexed = engine.classify(url, host, "news.example.net", type, index.candidateMask(host))
            assertEquals(url, original is FilterDecision.Allow, indexed is FilterDecision.Allow)
            if (original is FilterDecision.Block && indexed is FilterDecision.Block)
                assertEquals(url, original.rule.raw, indexed.rule.raw)
        }
        assertEquals(9, MappedDomainIndex.openOrBuild(folder, "c".repeat(64), engine)!!
            .candidateMask("ads.tracker.example"))
    }

    @Test fun largeDomainIndexHasNoFalseNegativesAndReportsLookupCost() {
        val engine = FilterEngine()
        repeat(10_000) { n ->
            engine.addRule(FilterListParser.parseNetworkRule("||ad$n.test^", FilterSource.SUBSCRIBED))
        }
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "native-domain-index-scale-test")
        val index = MappedDomainIndex.openOrBuild(folder, "b".repeat(64), engine)!!
        val hosts = (0 until 500).map { n ->
            if (n % 2 == 0) "sub.ad${n * 19}.test" else "site$n.example"
        }
        hosts.forEachIndexed { n, host ->
            assertEquals(host, if (n % 2 == 0) 8 else 0, index.candidateMask(host))
            val url = "https://$host/pixel.png"
            val plain = engine.classify(url, host, "news.example", ResourceType.IMAGE)
            val mapped = engine.classify(url, host, "news.example", ResourceType.IMAGE, index.candidateMask(host))
            assertEquals(url, plain is FilterDecision.Allow, mapped is FilterDecision.Allow)
        }
        val timingHosts = (0 until 500).map { n ->
            if (n % 20 == 0) "sub.ad${n * 19}.test" else "site$n.example"
        }
        fun measure(mapped: Boolean): Long {
            val start = System.nanoTime()
            repeat(4) {
                for (host in timingHosts) {
                    val url = "https://$host/pixel.png"
                    engine.classify(url, host, "news.example", ResourceType.IMAGE,
                        if (mapped) index.candidateMask(host) else null)
                }
            }
            return System.nanoTime() - start
        }
        repeat(2) { measure(false); measure(true) }
        val plain = mutableListOf<Long>()
        val mapped = mutableListOf<Long>()
        repeat(6) { round ->
            if (round % 2 == 0) {
                plain += measure(false); mapped += measure(true)
            } else {
                mapped += measure(true); plain += measure(false)
            }
        }
        println("Domain lookup comparison, 5% domain hits, median of 6 batches of 2000 requests: " +
            "Kotlin=${plain.sorted()[3]} ns, mapped/native=${mapped.sorted()[3]} ns")
    }
}
