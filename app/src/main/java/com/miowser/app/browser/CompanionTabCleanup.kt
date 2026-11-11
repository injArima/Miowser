package com.miowser.app.browser

internal const val COMPANION_TAB_GRACE_MS = 25_000L

/** One monotonic deadline per abandoned side-pane tab; activity cancels its countdown. */
internal class CompanionTabCleanup {
    private val ownedIds = mutableSetOf<Long>()
    private val deadlines = mutableMapOf<Long, Long>()

    data class Decision(val expiredIds: Set<Long>, val nextDelayMs: Long?)

    /** Only tabs actually created by the side pane may ever be cleaned up. */
    fun register(tabId: Long) { ownedIds.add(tabId) }

    fun ownedCandidates(ids: Set<Long>): Set<Long> = ids intersect ownedIds

    fun retainTabs(existingIds: Set<Long>) {
        ownedIds.retainAll(existingIds)
        deadlines.keys.retainAll(existingIds)
    }

    fun update(candidates: Set<Long>, protectedIds: Set<Long>, nowMs: Long): Decision {
        val inactive = ownedCandidates(candidates) - protectedIds
        deadlines.keys.retainAll(inactive)
        inactive.forEach { deadlines.getOrPut(it) { nowMs + COMPANION_TAB_GRACE_MS } }
        val expired = deadlines.filterValues { it <= nowMs }.keys.toSet()
        return Decision(expired, deadlines.values.filter { it > nowMs }.minOrNull()?.minus(nowMs))
    }
}
