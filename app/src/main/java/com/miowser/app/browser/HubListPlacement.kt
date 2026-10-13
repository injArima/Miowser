package com.miowser.app.browser

/** A stable layout stays ready for placement; only a confirmed manual removal may move it. */
internal class HubListPlacement {
    private var previous: List<String>? = null
    private var removedByUser: String? = null

    fun removedByUser(key: String) { removedByUser = key }

    fun update(keys: List<String>, allowed: Boolean): Boolean {
        val before = previous
        previous = keys
        if (!allowed) { removedByUser = null; return false }
        if (before == null || before == keys) return true
        val removed = removedByUser
        removedByUser = null
        // Reject insertions, bulk edits and reordered survivors, including rule/sort passes.
        return removed != null && removed in before && keys == before.filterNot { it == removed }
    }
}
