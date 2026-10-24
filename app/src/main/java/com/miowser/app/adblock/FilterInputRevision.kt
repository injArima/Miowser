package com.miowser.app.adblock

/** Only filter mutations invalidate builds. Never hold this monitor while doing disk IO.
 * Publication holds it briefly, so an input write cannot start between validation and install. */
internal class FilterInputRevision {
    private val lock = Any()
    private var version = 0L
    private var writers = 0

    fun <T> write(action: () -> T): T {
        synchronized(lock) { writers++; version++ }
        return try { action() } finally { synchronized(lock) { writers--; version++ } }
    }

    fun <T> readStable(read: () -> T): Pair<Long, T>? {
        val before = synchronized(lock) { if (writers == 0) version else null } ?: return null
        val value = read()
        return ifCurrent(before) { before to value }
    }

    fun <T> ifCurrent(expected: Long, action: () -> T): T? = synchronized(lock) {
        if (writers == 0 && version == expected) action() else null
    }
}
