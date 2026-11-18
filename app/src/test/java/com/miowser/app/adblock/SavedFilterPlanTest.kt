package com.miowser.app.adblock

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SavedFilterPlanTest {
    @Test fun allEnabledDefaultsCanUseBundledBaselineWithoutDownloads() {
        val plan = SavedFilterPlan(setOf("easylist", "easyprivacy", "peter-lowe"), emptySet())
        assertTrue(plan.requiresEasyList)
        assertTrue(plan.usesBundledEasyList)
        assertEquals(setOf("easyprivacy", "peter-lowe"), plan.missingIds)
        assertTrue(plan.status(0).contains("Bundled EasyList baseline active"))
        assertTrue(plan.status(0).contains("2 enabled list(s) have no saved copy"))
        assertTrue(plan.status(0).contains("Update filter lists to download"))
    }

    @Test fun disabledEasyListDoesNotRequireOrActivateBundledBaseline() {
        val plan = SavedFilterPlan(setOf("custom"), setOf("custom", "easylist"))
        assertFalse(plan.requiresEasyList)
        assertFalse(plan.usesBundledEasyList)
        assertTrue(plan.missingIds.isEmpty())
        assertFalse(plan.status(0).contains("EasyList"))
    }

    @Test fun allDisabledSelectionIsReadyWithoutABaselineOrDownloads() {
        val plan = SavedFilterPlan(emptySet(), emptySet())
        assertFalse(plan.requiresEasyList)
        assertFalse(plan.usesBundledEasyList)
        assertEquals("Saved filter selection active", plan.status(0))
    }

    @Test fun savedDisabledDefaultIsPreserved() {
        val disabled = FilterSubscription("easylist", "EasyList", EASYLIST_URL, true, false)
        val defaults = FilterSubscriptionStore.defaultSubscriptions(listOf(disabled))
        assertFalse(defaults.single { it.id == "easylist" }.enabled)
        assertTrue(defaults.filterNot { it.id == "easylist" }.all { it.enabled })
    }

    @Test fun allSavedDisabledDefaultsStayDisabled() {
        val disabled = FilterSubscriptionStore.defaultSubscriptions(emptyList()).map { it.copy(enabled = false) }
        assertTrue(FilterSubscriptionStore.defaultSubscriptions(disabled).none { it.enabled })
    }

    @Test fun corruptEasyListCacheUsesFreshBundledCandidateAtStartup() {
        val directory = Files.createTempDirectory("bad-easylist").toFile()
        try {
            val corrupt = directory.resolve("list-easylist.zip").apply { writeText("not a ZIP archive") }
            val files = FilterListFiles(directory)
            assertEquals(corrupt, files.existing("easylist"))
            var attempts = 0
            val rules = buildSavedFilterCandidate(hasWorkingGeneration = { false }) { excluded ->
                attempts++
                val candidate = mutableListOf("curated")
                if ("easylist" !in excluded) {
                    candidate.add("partial cache rule")
                    try { FilterListFiles.reader(files.existing("easylist")!!).use { it.readText() } }
                    catch (error: Exception) { throw UnreadableSavedFilter("easylist", error) }
                    fail("corrupt cache must not parse")
                }
                candidate.add("bundled EasyList rule")
                candidate
            }
            assertEquals(2, attempts)
            assertEquals(listOf("curated", "bundled EasyList rule"), rules)
        } finally { directory.deleteRecursively() }
    }

    @Test fun corruptUpdateCannotReplacePreviousWorkingGeneration() {
        val previous = listOf("working saved rules")
        var installed = previous
        val error = runCatching {
            installed = buildSavedFilterCandidate(hasWorkingGeneration = { true }) {
                throw UnreadableSavedFilter("easylist", IllegalStateException("bad cache EOF"))
            }
        }.exceptionOrNull()
        assertEquals("bad cache EOF", error?.message)
        assertSame(previous, installed)
    }

    @Test fun failedBundledFallbackThrowsInsteadOfClaimingReadiness() {
        var attempts = 0
        val error = runCatching {
            buildSavedFilterCandidate<Unit>(hasWorkingGeneration = { false }) {
                attempts++
                throw UnreadableSavedFilter("easylist", IllegalStateException("baseline unreadable"))
            }
        }.exceptionOrNull()
        assertEquals(2, attempts)
        assertEquals("baseline unreadable", error?.message)
    }
}
