package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSubscriptionUrlsTest {
    @Test
    fun `bulk paste skips saved and repeated URLs before download selection`() {
        val selected = FilterSubscriptionUrls.selectNew(
            existingUrls = listOf("https://example.com/ads.txt", EASYLIST_URL),
            pastedUrls = listOf(
                "HTTPS://EXAMPLE.COM:443/ads.txt#latest",
                EASYLIST_URL,
                "https://example.com/privacy.txt",
                "https://EXAMPLE.com:443/privacy.txt#copy",
                "https://example.com/annoyances.txt",
                "not-a-url"
            )
        )

        assertEquals(listOf("https://example.com/privacy.txt", "https://example.com/annoyances.txt"), selected.urls)
        assertEquals(3, selected.duplicates)
        assertEquals(1, selected.invalid)
    }

    @Test
    fun `distinct lists on one host are kept separate`() {
        val selected = FilterSubscriptionUrls.selectNew(
            emptyList(),
            listOf("https://example.com/ads.txt", "https://example.com/privacy.txt")
        )

        assertEquals(2, selected.urls.size)
        assertEquals(0, selected.duplicates)
    }
}
