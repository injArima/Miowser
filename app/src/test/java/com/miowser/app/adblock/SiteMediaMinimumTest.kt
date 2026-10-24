package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteMediaMinimumTest {
    @Test
    fun `site value including off overrides browser minimum`() {
        val values = parseSiteMediaMinimums(setOf("example.com|0", "videos.example.com|120"))
        assertEquals(0, resolveSiteMediaMinimum("example.com", values, 60))
        assertEquals(120, resolveSiteMediaMinimum("videos.example.com", values, 60))
        assertEquals(60, resolveSiteMediaMinimum("other.example.com", values, 60))
    }

    @Test
    fun `invalid site values are ignored`() {
        assertEquals(emptyMap<String, Int>(), parseSiteMediaMinimums(
            setOf("example.com|-1", "example.org|1801", "example.net|oops", "broken")
        ))
    }
}
