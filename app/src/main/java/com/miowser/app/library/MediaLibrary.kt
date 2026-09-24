package com.miowser.app.library

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

private const val MAX_LIBRARY_ENTRIES = 500

/** Compose-observable, persistent source for the Hub's Library tab. */
object MediaLibrary {
    val entries = mutableStateListOf<LibraryEntry>()

    @Volatile private var restored = false

    fun restore(context: Context) {
        if (restored) return
        restored = true
        entries.addAll(LibraryStore.load(context).sortedByDescending { it.lastPlayedAt })
        LibraryThumbnailStore.pruneAsync(context, entries.mapNotNull { it.thumbnailUrl })
    }

    /**
     * Adds a first playback or updates and moves a repeated item to the front. Existing resume
     * progress and metadata that the new handoff does not know yet are retained.
     */
    fun recordOpened(context: Context, incoming: LibraryEntry): LibraryEntry {
        val existingIndex = entries.indexOfFirst { it.identityKey == incoming.identityKey }
        val existing = entries.getOrNull(existingIndex)
        val merged = mergeOpened(existing, incoming).let { entry ->
            if (existing == null && entry.folderId == null) {
                entry.copy(folderId = LibraryFolders.matchingFolderId(context, entry.pageUrl))
            } else entry
        }
        if (existingIndex >= 0) entries.removeAt(existingIndex)
        entries.add(0, merged)
        while (entries.size > MAX_LIBRARY_ENTRIES) entries.removeAt(entries.lastIndex)
        persist(context)
        return merged
    }

    /** Called at meaningful player boundaries (pause/exit), not on every playback clock tick. */
    fun updateProgress(
        context: Context,
        identityKey: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val index = entries.indexOfFirst { it.identityKey == identityKey }
        if (index < 0) return
        val current = entries[index]
        entries[index] = current.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        persist(context)
    }

    /** Links a captured player frame to its existing Library record. */
    fun updateThumbnail(context: Context, identityKey: String, thumbnailUri: String) {
        val index = entries.indexOfFirst { it.identityKey == identityKey }
        if (index < 0) return
        val current = entries[index]
        if (current.thumbnailUrl == thumbnailUri) return
        entries[index] = current.copy(thumbnailUrl = thumbnailUri)
        persist(context)
    }

    fun remove(context: Context, entry: LibraryEntry) {
        entries.removeAll { it.identityKey == entry.identityKey }
        persist(context)
    }

    fun clear(context: Context) {
        entries.clear()
        persist(context)
    }

    fun moveToFolder(context: Context, identityKey: String, folderId: String?) {
        val index = entries.indexOfFirst { it.identityKey == identityKey }
        if (index < 0 || entries[index].folderId == folderId) return
        entries[index] = entries[index].copy(folderId = folderId)
        persist(context)
    }

    fun addExistingFromDomain(context: Context, folderId: String, domain: String) {
        var changed = false
        entries.indices.forEach { index ->
            val entry = entries[index]
            if (entry.folderId == null && libraryDomainMatches(entry.pageUrl, domain)) {
                entries[index] = entry.copy(folderId = folderId)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    fun unfileFolder(context: Context, folderId: String) {
        var changed = false
        entries.indices.forEach { index ->
            if (entries[index].folderId == folderId) {
                entries[index] = entries[index].copy(folderId = null)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    /** Restores records removed by an Undo action with one disk write and original recency order. */
    fun restoreRemoved(context: Context, removed: Collection<LibraryEntry>) {
        if (removed.isEmpty()) return
        val restored = (entries.toList() + removed)
            .distinctBy { it.identityKey }
            .sortedByDescending { it.lastPlayedAt }
            .take(MAX_LIBRARY_ENTRIES)
        entries.clear()
        entries.addAll(restored)
        persist(context)
    }

    private fun persist(context: Context) = LibraryStore.save(context, entries)
}

/** Pure merge policy kept outside the Android-dependent repository so it can be unit-tested. */
fun mergeOpened(existing: LibraryEntry?, incoming: LibraryEntry): LibraryEntry {
    if (existing == null) return incoming.copy(
        positionMs = incoming.positionMs.coerceAtLeast(0L),
        durationMs = incoming.durationMs.coerceAtLeast(0L)
    )
    return incoming.copy(
        pageUrl = incoming.pageUrl ?: existing.pageUrl,
        title = incoming.title.ifBlank { existing.title },
        sourceLabel = incoming.sourceLabel.ifBlank { existing.sourceLabel },
        mediaType = incoming.mediaType ?: existing.mediaType,
        userAgent = incoming.userAgent ?: existing.userAgent,
        thumbnailUrl = incoming.thumbnailUrl ?: existing.thumbnailUrl,
        pageTitle = incoming.pageTitle ?: existing.pageTitle,
        videoName = incoming.videoName ?: existing.videoName,
        folderId = incoming.folderId ?: existing.folderId,
        // The handoff's actual start point is authoritative: opening from a Library card passes
        // its resume point, while intentionally starting the same URL elsewhere passes zero and
        // correctly resets the watched-progress bar instead of leaving stale progress visible.
        positionMs = incoming.positionMs.coerceAtLeast(0L),
        durationMs = incoming.durationMs.takeIf { it > 0L } ?: existing.durationMs
    )
}
