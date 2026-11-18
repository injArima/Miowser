package com.miowser.app.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterCacheIdentityTest {
    @Test fun renameAndDownloadDiagnosticsDoNotInvalidateMatchingInputs() {
        val row = FilterSubscription("custom", "Original name", "https://example.org/list", false, true)
        val renamed = row.copy(name = "Renamed", lastError = "download failed", lastUpdatedAt = 200)
        fun parts(value: FilterSubscription) = FilterCacheIdentity.subscriptionParts(value, "zip", 1200, 5000)
        assertEquals(parts(row), parts(renamed))
        assertNotEquals(parts(row), parts(row.copy(url = "https://example.org/other")))
        assertNotEquals(parts(row), parts(row.copy(id = "other")))
        assertNotEquals(parts(row), parts(row.copy(isBuiltIn = true)))
        assertTrue(parts(row.copy(enabled = false)).isEmpty())
    }

    private fun identity(easylist: String = "||ads.example^", suffix: String = "com\norg") = FilterCacheIdentity {
        listOf("easylist" to { easylist.byteInputStream() }, "public-suffix" to { suffix.byteInputStream() })
    }

    private fun key(assets: String = identity().digest, resources: String = "music-v1",
                    format: String = "compiled-v10", parser: String = FilterCacheIdentity.PARSER_COMPATIBILITY,
                    subscription: String = "custom:zip:1200:5000", manual: String = "manual:20:4000") =
        FilterCacheIdentity.fingerprint(listOf(format, parser, assets, resources, subscription, manual))

    @Test fun sameInputsAcrossCompatibleAppInstallsHaveSameKey() {
        // Separate process identities: no APK version, install path or update time enters the key.
        val previousInstall = identity()
        val updatedInstall = identity()
        assertEquals(key(assets = previousInstall.digest), key(assets = updatedInstall.digest))
    }

    @Test fun everyCompatibilityAndMutableInputChangeInvalidatesSnapshot() {
        val original = key()
        listOf(
            key(format = "compiled-v11"), key(parser = "parser-5"),
            key(assets = identity(easylist = "||changed.example^").digest),
            key(assets = identity(suffix = "com\norg\nexample").digest),
            key(resources = "music-v2"), key(subscription = "custom:zip:1200:5001"),
            key(subscription = "other:zip:1200:5000"), key(manual = "manual:20:4001")
        ).forEach { assertNotEquals(original, it) }
    }

    @Test fun immutableAssetsAreOpenedOnlyOnceAcrossRepeatedBuildFingerprints() {
        var reads = 0
        val assets = FilterCacheIdentity {
            listOf("easylist" to { reads++; "rules".byteInputStream() },
                "public-suffix" to { reads++; "suffixes".byteInputStream() })
        }
        repeat(10) { key(assets = assets.digest, manual = "manual:$it") }
        assertEquals(2, reads)
    }

    @Test fun fieldBoundariesCannotCollide() {
        assertNotEquals(FilterCacheIdentity.fingerprint(listOf("ab", "c")),
            FilterCacheIdentity.fingerprint(listOf("a", "bc")))
    }
}
