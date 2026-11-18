package com.miowser.app.adblock

import org.junit.Assert.*
import org.junit.Test

class ServiceWorkerRequestPolicyTest {
    private val ad = "https://ads.example/ad.js"

    @Test fun workerUsesItsInitiatorWithoutDependingOnTheActiveTab() {
        val context = ServiceWorkerRequestPolicy.context(ad, mapOf(
            "rEfErEr" to "https://music.youtube.com/sw.js?private=value"))
        assertEquals("https://music.youtube.com", context?.origin)
        assertEquals("music.youtube.com", context?.host)
        assertFalse(context.toString().contains("private=value"))
    }

    @Test fun corsOriginTakesPrecedenceAndOpaqueOriginsRemainUnattributed() {
        assertEquals("https://player.example", ServiceWorkerRequestPolicy.context(ad, mapOf(
            "Origin" to "https://player.example", "Referer" to "https://other.example/page"))?.origin)
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf(
            "Origin" to "null", "Referer" to "https://music.youtube.com/")))
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf(
            "Origin" to "invalid", "Referer" to "https://music.youtube.com/")))
    }

    @Test fun missingContextNeverTreatsTheAdServerAsTheSourcePage() {
        assertNull(ServiceWorkerRequestPolicy.context(ad, emptyMap()))
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Origin" to "null")))
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Referer" to "about:blank")))
    }

    @Test fun invalidOrPrivilegedUrlsCannotReachTheMatcher() {
        for (url in listOf("file:///private", "content://downloads/1", "javascript:1", "not a URL")) {
            assertNull(ServiceWorkerRequestPolicy.context(url, mapOf("Origin" to "https://music.youtube.com")))
            assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Origin" to url)))
        }
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Referer" to "https://user:password@music.youtube.com/")))
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Origin" to "https://music.youtube.com:" + "9".repeat(100))))
        assertNull(ServiceWorkerRequestPolicy.context(ad, mapOf("Referer" to "https://music.youtube.com/" + "a".repeat(4096))))
    }

    @Test fun originNormalizationPreservesNonDefaultPortsAndDropsPageDetails() {
        val context = ServiceWorkerRequestPolicy.context(ad, mapOf("Origin" to "HTTPS://PLAYER.EXAMPLE:8443/"))
        assertEquals("https://player.example:8443", context?.origin)
        assertEquals("player.example", context?.host)
    }
}
