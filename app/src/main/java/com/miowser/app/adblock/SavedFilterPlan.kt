package com.miowser.app.adblock

/** Missing downloads are optional at startup; a selected EasyList needs saved or shipped rules. */
internal class SavedFilterPlan(val enabledIds: Set<String>, private val cachedIds: Set<String>) {
    val requiresEasyList: Boolean get() = "easylist" in enabledIds
    val usesBundledEasyList: Boolean get() = requiresEasyList && "easylist" !in cachedIds
    val missingIds: Set<String> get() = enabledIds - cachedIds - "easylist"

    fun status(skipped: Int): String = buildList {
        add(if (usesBundledEasyList) "Bundled EasyList baseline active" else "Saved filter selection active")
        if (missingIds.isNotEmpty()) add("${missingIds.size} enabled list(s) have no saved copy; Update filter lists to download")
        if (skipped > 0) add("$skipped saved source(s) skipped due to errors")
    }.joinToString("; ")
}

internal class UnreadableSavedFilter(val id: String, cause: Exception) : Exception(cause)

/** Failed readers may already have delivered rules. Retry a fresh candidate, never partial data. */
internal fun <T> buildSavedFilterCandidate(
    hasWorkingGeneration: () -> Boolean,
    onSkipped: (UnreadableSavedFilter) -> Unit = {},
    build: (Set<String>) -> T
): T {
    val excluded = mutableSetOf<String>()
    while (true) {
        try { return build(excluded.toSet()) }
        catch (error: UnreadableSavedFilter) {
            if (hasWorkingGeneration() || !excluded.add(error.id) || excluded.size > 64) throw error.cause ?: error
            onSkipped(error)
        }
    }
}
