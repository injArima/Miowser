package com.miowser.app.adblock

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledEasyListTest {
    @Test
    fun bundledListBuildsAndBlocksARealDomainRule() {
        val text = File("src/main/assets/easylist.txt").readText()
        val parsed = FilterListParser.parse(text, FilterSource.SUBSCRIBED)
        val engine = FilterEngine()
        parsed.networkRules.forEach(engine::addRule)
        assertTrue(parsed.networkRules.size > 10_000)
        assertTrue(
            engine.classify(
                "https://000491b06a.com/ad.js",
                "000491b06a.com",
                "example.org",
                ResourceType.SCRIPT
            ) is FilterDecision.Block
        )
    }
}
