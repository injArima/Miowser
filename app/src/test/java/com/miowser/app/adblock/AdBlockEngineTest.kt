package com.miowser.app.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockEngineTest {

    private fun ruleFor(domain: String?, selector: String, excludedDomains: List<String> = emptyList()) =
        CosmeticRule(domain = domain, selector = selector, isException = false, source = FilterSource.SUBSCRIBED, excludedDomains = excludedDomains)

    @Test
    fun `hides a selector on the domain it's scoped to`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad"))
        assertTrue(engine.cosmeticCssFor("example.com").contains(".ad"))
    }

    @Test
    fun `hides on a subdomain of the scoped domain too`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad"))
        assertTrue(engine.cosmeticCssFor("www.example.com").contains(".ad"))
    }

    @Test
    fun `does not hide a selector scoped to a different domain`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad"))
        assertFalse(engine.cosmeticCssFor("other.com").contains(".ad"))
    }

    @Test
    fun `a domain exclusion suppresses the selector on exactly that domain`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad", excludedDomains = listOf("sub.example.com")))
        assertFalse(engine.cosmeticCssFor("sub.example.com").contains(".ad"))
    }

    @Test
    fun `a domain exclusion still lets the selector apply on other subdomains of the included domain`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad", excludedDomains = listOf("sub.example.com")))
        assertTrue(engine.cosmeticCssFor("other.example.com").contains(".ad"))
        assertTrue(engine.cosmeticCssFor("example.com").contains(".ad"))
    }

    @Test
    fun `a domain exclusion also covers subdomains of the excluded domain itself`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad", excludedDomains = listOf("sub.example.com")))
        assertFalse(engine.cosmeticCssFor("deep.sub.example.com").contains(".ad"))
    }

    @Test
    fun `a generic rule with a domain exclusion applies everywhere except that domain`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor(null, ".ad", excludedDomains = listOf("foo.com")))
        assertTrue(engine.cosmeticCssFor("example.com").contains(".ad"))
        assertFalse(engine.cosmeticCssFor("foo.com").contains(".ad"))
    }

    @Test
    fun `an exception rule suppresses the selector regardless of any exclusion list`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad"))
        engine.addCosmeticRule(
            CosmeticRule(domain = "example.com", selector = ".ad", isException = true, source = FilterSource.SUBSCRIBED)
        )
        assertFalse(engine.cosmeticCssFor("example.com").contains(".ad"))
    }

    @Test
    fun `does not emit a domain scoped selector on an unrelated domain`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor("example.com", ".ad"))
        assertFalse(engine.cosmeticCssFor("unrelated.com").contains(".ad {"))
    }

    @Test
    fun `emits cosmetic selectors as independent CSS rules`() {
        val engine = AdBlockEngine()
        engine.addCosmeticRule(ruleFor(null, ".simple-ad"))
        engine.addCosmeticRule(ruleFor(null, ".rail:has(.ad-label)"))
        val css = engine.cosmeticCssFor("example.com")
        assertTrue(css.contains(".simple-ad { display: none !important; }"))
        assertTrue(css.contains(".rail:has(.ad-label) { display: none !important; }"))
        assertFalse(css.contains(",\n"))
    }
}
