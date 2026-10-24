package com.miowser.app.browser

import com.miowser.app.library.LibraryEntry

/** A few recent derived lists; no retained grids, images or collectors. */
internal class HubLibraryProjection {
    private data class Key(val revision: Long, val folder: String, val sort: HubLibrarySort, val query: String)
    private val results = object : LinkedHashMap<Key, List<LibraryEntry>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<LibraryEntry>>?): Boolean =
            size > 4
    }
    fun peek(revision: Long, folder: String, sort: HubLibrarySort, query: String): List<LibraryEntry>? =
        if (revision < 0) null else results[Key(revision, folder, sort, query)]
    fun publish(revision: Long, folder: String, sort: HubLibrarySort, query: String, entries: List<LibraryEntry>) {
        if (revision < 0) return
        results[Key(revision, folder, sort, query)] = entries.toList()
    }
}
