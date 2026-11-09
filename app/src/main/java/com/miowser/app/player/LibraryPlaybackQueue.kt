package com.miowser.app.player

import com.miowser.app.library.LibraryEntry

/** Snapshot of the full query order, independent of later progress/recency updates. */
internal data class LibraryPlaybackQueue(val identityKeys: List<String>) {
    fun entries(resolve: (String) -> LibraryEntry?): List<LibraryEntry> = identityKeys.mapNotNull(resolve)

    fun neighbor(currentKey: String, direction: Int, resolve: (String) -> LibraryEntry?): LibraryEntry? {
        require(direction == -1 || direction == 1)
        val current = identityKeys.indexOf(currentKey)
        if (current < 0) return null
        var index = current + direction
        while (index in identityKeys.indices) {
            resolve(identityKeys[index])?.let { return it }
            index += direction
        }
        return null
    }
}
