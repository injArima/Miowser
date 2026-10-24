package com.miowser.app.library

const val MAX_EXPLICIT_LIBRARY_ENTRIES = 5_000
private const val MAX_RECENT_LIBRARY_ENTRIES = 500

/** Explicit saves and manual placements survive automatic recent-play eviction.
 * Legacy records decode as saved; migration never guesses which old entries were intentional. */
fun retainLibraryRecords(records: List<LibraryEntry>): List<LibraryEntry> {
    var recent = 0
    return records.filter { it.explicitlySaved || it.manualFolder || ++recent <= MAX_RECENT_LIBRARY_ENTRIES }
}
