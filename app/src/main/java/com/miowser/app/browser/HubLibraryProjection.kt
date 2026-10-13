package com.miowser.app.browser

import com.miowser.app.library.LibraryEntry

/** One derived list, no retained grid, images or collectors. Repository revision prevents stale reuse. */
internal class HubLibraryProjection {
    private data class Key(val revision: Long, val folder: String, val sort: HubLibrarySort, val query: String)
    private var key: Key? = null
    private var result: List<LibraryEntry>? = null
    fun peek(revision: Long, folder: String, sort: HubLibrarySort, query: String): List<LibraryEntry>? =
        result.takeIf { revision >= 0 && key == Key(revision, folder, sort, query) }
    fun publish(revision: Long, folder: String, sort: HubLibrarySort, query: String, entries: List<LibraryEntry>) {
        if (revision < 0) return
        key = Key(revision, folder, sort, query)
        result = entries.toList()
    }
}
