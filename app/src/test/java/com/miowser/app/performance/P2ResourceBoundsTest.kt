package com.miowser.app.performance

import org.junit.Assert.*
import org.junit.Test

class P2ResourceBoundsTest {
    @Test fun weightedCacheBoundsKeysValuesAndCountAndKeepsRecentlyUsedItems() {
        val cache = WeightedCache<String, String>(3, 12) { key, value -> key.length + value.length }
        cache["a"] = "111"; cache["b"] = "222"; cache["c"] = "333"
        assertEquals("111", cache["a"])
        cache["d"] = "444"; assertNull(cache["b"]); assertEquals(12L, cache.retainedBytes())
        cache["a"] = "oversized value"; assertNull(cache["a"])
        cache.remove("c"); assertEquals(1, cache.size()); assertEquals(4L, cache.retainedBytes())
    }
    @Test fun decoderRejectsUnsafeBoundsAndSamplesBeforePixelsAreAllocated() {
        assertNull(boundedImageSample(-1, 0, 128))
        assertNull(boundedImageSample(40_000, 1, 128))
        assertNull(boundedImageSample(16_384, 16_384, 128))
        val sample = boundedImageSample(8000, 6000, 128)!!
        assertTrue(8000 / sample <= 128 && 6000 / sample <= 128)
        assertEquals(1, boundedImageSample(64, 64, 128))
    }
}
