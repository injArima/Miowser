package com.miowser.app.player

import com.miowser.app.library.LibraryEntry

/** Snapshot of the visible Library order, independent of later progress/recency updates. */
internal data class LibraryPlaybackQueue(val identityKeys: List<String>) {
    fun entries(library: List<LibraryEntry>): List<LibraryEntry> {
        val byKey = library.associateBy(LibraryEntry::identityKey)
        return identityKeys.mapNotNull(byKey::get)
    }

    fun neighbor(currentKey: String, library: List<LibraryEntry>, direction: Int): LibraryEntry? {
        require(direction == -1 || direction == 1)
        val current = identityKeys.indexOf(currentKey)
        if (current < 0) return null
        val byKey = library.associateBy(LibraryEntry::identityKey)
        var index = current + direction
        while (index in identityKeys.indices) {
            byKey[identityKeys[index]]?.let { return it }
            index += direction
        }
        return null
    }

    companion object {
        fun fromVisible(entries: List<LibraryEntry>): LibraryPlaybackQueue =
            LibraryPlaybackQueue(entries.map(LibraryEntry::identityKey).distinct())
    }
}
