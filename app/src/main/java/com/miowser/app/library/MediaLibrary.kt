package com.miowser.app.library

import android.content.Context
import com.miowser.app.persistence.Persistence
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf



/** Compose-observable, persistent source for the Hub's Library tab. */
object MediaLibrary {
    private val mutableRecords = mutableStateListOf<LibraryEntry>()
    val entries: List<LibraryEntry> get() = mutableRecords

    @Volatile private var revision = 0L
    val contentRevision: Long get() = revision
    private val layoutVersion = mutableLongStateOf(0L)
    private val progressVersion = mutableLongStateOf(0L)
    val layoutRevision: Long get() = layoutVersion.longValue
    val progressSortRevision: Long get() = progressVersion.longValue
    private var cleanup: kotlinx.coroutines.Job? = null
    @Volatile private var restored = false

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LibraryStore.load(context).sortedByDescending { it.lastPlayedAt } }
        if (restored) return
        mutableRecords.addAll(records)
        layoutVersion.longValue++
        progressVersion.longValue++
        restored = true
        LibraryThumbnailStore.pruneAsync(context, mutableRecords.mapNotNull { it.thumbnailUrl })
        LibrarySubtitleStore.pruneAsync(context, mutableRecords)
    }

    fun restore(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        mutableRecords.addAll(LibraryStore.load(context).sortedByDescending { it.lastPlayedAt })
        layoutVersion.longValue++
        progressVersion.longValue++
        restored = true
        LibraryThumbnailStore.pruneAsync(context, mutableRecords.mapNotNull { it.thumbnailUrl })
        LibrarySubtitleStore.pruneAsync(context, mutableRecords)
    }

    /**
     * Adds a first playback or updates and moves a repeated item to the front. Existing resume
     * progress and metadata that the new handoff does not know yet are retained.
     */
    fun recordOpened(context: Context, incoming: LibraryEntry): LibraryEntry {
        Persistence.requireOwner()
        val existingIndex = mutableRecords.indexOfFirst { it.identityKey == incoming.identityKey }
        val existing = mutableRecords.getOrNull(existingIndex)
        val merged = mergeOpened(existing, incoming).let { entry ->
            if (existing == null && entry.folderId == null) {
                entry.copy(folderId = LibraryFolders.routeNewVideo(context, entry))
            } else if (existing != null && !existing.manualFolder &&
                (existing.videoName != entry.videoName || existing.pageTitle != entry.pageTitle ||
                    existing.title != entry.title || existing.pageUrl != entry.pageUrl)) {
                entry.copy(folderId = LibraryFolders.routeAfterTitleChange(context, entry))
            } else entry
        }
        if (existingIndex >= 0) mutableRecords.removeAt(existingIndex)
        mutableRecords.add(0, merged)
        trimRecent(context)
        persist(context)
        return merged
    }

    /** Save a detected video without changing the progress or recency of an existing entry. */
    fun add(context: Context, entry: LibraryEntry): Boolean {
        Persistence.requireOwner()
        val existingIndex = mutableRecords.indexOfFirst { it.identityKey == entry.identityKey }
        if (existingIndex >= 0) {
            val existing = mutableRecords[existingIndex]
            if (!existing.explicitlySaved && !existing.manualFolder) requireSaveCapacity()
            var refreshed = existing.copy(
                title = entry.title.ifBlank { existing.title },
                pageTitle = entry.pageTitle ?: existing.pageTitle,
                videoName = entry.videoName ?: existing.videoName,
                pageUrl = entry.pageUrl ?: existing.pageUrl,
                hlsRenditions = entry.hlsRenditions.ifEmpty { existing.hlsRenditions },
                explicitlySaved = true
            )
            if (refreshed != existing) {
                if (!existing.manualFolder) {
                    refreshed = refreshed.copy(folderId = LibraryFolders.routeAfterTitleChange(context, refreshed))
                }
                mutableRecords[existingIndex] = refreshed
                persist(context)
            }
            return false
        }
        requireSaveCapacity()
        mutableRecords.add(0, entry.copy(
            explicitlySaved = true,
            folderId = entry.folderId ?: LibraryFolders.routeNewVideo(context, entry)
        ))
        trimRecent(context)
        persist(context)
        return true
    }

    fun remainingSavedCapacity(): Int =
        MAX_EXPLICIT_LIBRARY_ENTRIES - mutableRecords.count { it.explicitlySaved || it.manualFolder }

    fun savedSlotsNeededForImport(uris: Collection<String>): Int {
        val byKey = mutableRecords.associateBy(LibraryEntry::identityKey)
        return uris.asSequence().distinct().count { uri ->
            val existing = byKey["${LibrarySourceKind.LOCAL_FILE.name}:$uri"]
            existing == null || (!existing.explicitlySaved && !existing.manualFolder)
        }
    }

    /** Imported content URIs retain their original files and manual folder placement. */
    fun importLocalBatch(context: Context, entries: List<LibraryEntry>): Int {
        Persistence.requireOwner()
        restore(context)
        val incoming = entries.filter { it.sourceKind == LibrarySourceKind.LOCAL_FILE }
            .distinctBy(LibraryEntry::identityKey)
        requireSaveCapacity(savedSlotsNeededForImport(incoming.map(LibraryEntry::mediaUri)))
        val indices = mutableRecords.withIndex().associate { it.value.identityKey to it.index }
        val fresh = mutableListOf<LibraryEntry>()
        var filedExisting = 0
        incoming.forEach { entry ->
            val index = indices[entry.identityKey]
            if (index == null) fresh.add(entry)
            else {
                val previous = mutableRecords[index]
                if (!previous.manualFolder) {
                    mutableRecords[index] = previous.copy(folderId = entry.folderId,
                        manualFolder = true, explicitlySaved = true)
                    filedExisting++
                }
            }
        }
        if (fresh.isNotEmpty()) mutableRecords.addAll(0, fresh)
        if (fresh.isNotEmpty() || filedExisting > 0) persist(context)
        return fresh.size + filedExisting
    }

    /** Called at meaningful player boundaries (pause/exit), not on every playback clock tick. */
    fun updateProgress(
        context: Context,
        identityKey: String,
        positionMs: Long,
        durationMs: Long
    ) {
        Persistence.requireOwner()
        val index = mutableRecords.indexOfFirst { it.identityKey == identityKey }
        if (index < 0) return
        val current = mutableRecords[index]
        val updated = current.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        if (updated == current) return
        mutableRecords[index] = updated
        persist(context, ProjectionChange.PROGRESS)
    }

    /** Links a captured player frame to its existing Library record. */
    fun updateThumbnail(context: Context, identityKey: String, thumbnailUri: String) {
        Persistence.requireOwner()
        val index = mutableRecords.indexOfFirst { it.identityKey == identityKey }
        if (index < 0) return
        val current = mutableRecords[index]
        if (current.thumbnailUrl == thumbnailUri) return
        if (!LibraryThumbnailStore.shouldReplace(current.thumbnailUrl, thumbnailUri)) return
        mutableRecords[index] = current.copy(thumbnailUrl = thumbnailUri)
        persist(context, ProjectionChange.DETAIL)
    }

    /** The selected video resolution may change during adaptive playback. */
    fun updateQuality(context: Context, identityKey: String, height: Int) {
        Persistence.requireOwner()
        if (height <= 0) return
        val index = mutableRecords.indexOfFirst { it.identityKey == identityKey }
        if (index < 0 || mutableRecords[index].qualityHeight == height) return
        mutableRecords[index] = mutableRecords[index].copy(qualityHeight = height)
        persist(context, ProjectionChange.DETAIL)
    }

    /** Keeps only the VTT the viewer chose, including the headers needed to reopen it. */
    fun updateSubtitle(context: Context, identityKey: String, subtitle: LibrarySubtitle?) {
        Persistence.requireOwner()
        val index = mutableRecords.indexOfFirst { it.identityKey == identityKey }
        if (index < 0) return
        val current = mutableRecords[index]
        if (current.selectedSubtitle == subtitle) return
        mutableRecords[index] = current.copy(selectedSubtitle = subtitle)
        persist(context, ProjectionChange.DETAIL)
        scheduleCleanup(context)
    }

    /** Reconnect a restored local Library card when Android requires a new document grant. */
    fun relinkLocal(context: Context, oldUri: String, newUri: String) {
        Persistence.requireOwner()
        LocalInspectionCache.forget(oldUri)
        LocalInspectionCache.forget(newUri)
        val index = mutableRecords.indexOfFirst {
            it.sourceKind == LibrarySourceKind.LOCAL_FILE && it.mediaUri == oldUri
        }
        if (index < 0 || oldUri == newUri) return
        mutableRecords[index] = mutableRecords[index].copy(mediaUri = newUri, qualityHeight = null, thumbnailUrl = null)
        persist(context)
        scheduleCleanup(context)
    }

    fun remove(context: Context, entry: LibraryEntry) {
        Persistence.requireOwner()
        removeAll(context, listOf(entry))
    }

    fun removeAll(context: Context, removed: Collection<LibraryEntry>) {
        Persistence.requireOwner()
        val keys = removed.map { it.identityKey }.toSet()
        if (mutableRecords.removeAll { it.identityKey in keys }) { persist(context); scheduleCleanup(context) }
    }

    fun clear(context: Context) {
        Persistence.requireOwner()
        mutableRecords.clear()
        persist(context)
        scheduleCleanup(context)
    }

    fun moveToFolder(context: Context, identityKey: String, folderId: String?) {
        Persistence.requireOwner()
        moveToFolder(context, setOf(identityKey), folderId)
    }

    fun moveToFolder(context: Context, identities: Set<String>, folderId: String?) {
        Persistence.requireOwner()
        val additional = mutableRecords.count { it.identityKey in identities && !it.explicitlySaved && !it.manualFolder }
        if (additional > 0) requireSaveCapacity(additional)
        var changed = false
        mutableRecords.indices.forEach { index ->
            val entry = mutableRecords[index]
            if (entry.identityKey in identities && (entry.folderId != folderId || !entry.manualFolder)) {
                mutableRecords[index] = entry.copy(folderId = folderId, manualFolder = true)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    fun addExistingFromDomain(context: Context, folderId: String, domain: String) {
        Persistence.requireOwner()
        var changed = false
        mutableRecords.indices.forEach { index ->
            val entry = mutableRecords[index]
            if (!entry.manualFolder && entry.folderId == null && libraryDomainMatches(entry.pageUrl, domain)) {
                mutableRecords[index] = entry.copy(folderId = folderId)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    suspend fun countRuleReorganization(context: Context, folderId: String): Int =
        ruleReorganization(context, folderId, apply = false)
    suspend fun applyRuleReorganization(context: Context, folderId: String): Int =
        ruleReorganization(context, folderId, apply = true)

    private suspend fun ruleReorganization(context: Context, folderId: String, apply: Boolean): Int {
        Persistence.requireOwner()
        repeat(3) {
            val recordsRevision = revision
            val foldersRevision = LibraryFolders.revision
            val folder = LibraryFolders.entries.firstOrNull { it.id == folderId } ?: return 0
            val sourceId = LibraryFolders.sourceForRule(context, folderId)
            val router = LibraryFolders.router()
            val records = mutableRecords.toList()
            val destinations = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                planLibraryReorganization(records, router, sourceId, folder.parentId != null)
            }
            if (revision != recordsRevision || LibraryFolders.revision != foldersRevision) return@repeat
            if (apply && destinations.isNotEmpty()) {
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    mutableRecords.indices.forEach { index ->
                        val entry = mutableRecords[index]
                        if (destinations.containsKey(entry.identityKey))
                            mutableRecords[index] = entry.copy(folderId = destinations[entry.identityKey])
                    }
                }
                persist(context)
            }
            return destinations.size
        }
        throw IllegalStateException("Library changed while organizing. Please try again.")
    }

    fun relocateFolder(context: Context, folderId: String, destinationId: String?) {
        Persistence.requireOwner()
        var changed = false
        mutableRecords.indices.forEach { index ->
            if (mutableRecords[index].folderId == folderId) {
                mutableRecords[index] = mutableRecords[index].copy(folderId = destinationId)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    /** Restores records removed by an Undo action with one disk write and original recency order. */
    fun restoreRemoved(context: Context, removed: Collection<LibraryEntry>) {
        Persistence.requireOwner()
        if (removed.isEmpty()) return
        val restored = (mutableRecords.toList() + removed)
            .distinctBy { it.identityKey }
            .sortedByDescending { it.lastPlayedAt }
            .let(::retainLibraryRecords)
        mutableRecords.clear()
        mutableRecords.addAll(restored)
        persist(context)
    }

    /** Undo a folder operation, restoring moved entries and removed Library records alike. */
    fun restoreFolderEntries(context: Context, originals: Collection<LibraryEntry>) {
        Persistence.requireOwner()
        if (originals.isEmpty()) return
        val byId = originals.associateBy { it.identityKey }
        val existingIds = mutableRecords.mapTo(HashSet()) { it.identityKey }
        val restored = (mutableRecords.map { byId[it.identityKey] ?: it } +
            originals.filter { it.identityKey !in existingIds })
            .sortedByDescending { it.lastPlayedAt }
            .let(::retainLibraryRecords)
        mutableRecords.clear()
        mutableRecords.addAll(restored)
        persist(context)
    }

    private fun requireSaveCapacity(additional: Int = 1) {
        check(mutableRecords.count { it.explicitlySaved || it.manualFolder } + additional <= MAX_EXPLICIT_LIBRARY_ENTRIES) {
            "Library holds 5,000 saved videos. Remove a saved video before saving another."
        }
    }
    private fun trimRecent(context: Context) {
        val retained = retainLibraryRecords(mutableRecords)
        if (retained.size != mutableRecords.size) {
            mutableRecords.clear(); mutableRecords.addAll(retained)
            scheduleCleanup(context)
        }
    }
    private fun scheduleCleanup(context: Context) {
        cleanup?.cancel()
        cleanup = com.miowser.app.startup.AppStartup.scope.launch {
            // Undo remains usable before obsolete private assets are reclaimed.
            kotlinx.coroutines.delay(60_000)
            Persistence.afterWrites {
                val records = mutableRecords.toList()
                val capturedRevision = revision
                com.miowser.app.startup.AppStartup.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    LibraryThumbnailStore.pruneAsync(context, records.mapNotNull { it.thumbnailUrl }) { revision == capturedRevision }
                    LibrarySubtitleStore.pruneAsync(context, records) { revision == capturedRevision }
                }
            }
        }
    }
    private enum class ProjectionChange { STRUCTURE, PROGRESS, DETAIL }

    private fun persist(
        context: Context,
        change: ProjectionChange = ProjectionChange.STRUCTURE
    ): java.util.concurrent.CompletableFuture<Boolean> {
        revision++
        when (change) {
            ProjectionChange.STRUCTURE -> {
                layoutVersion.longValue++
                progressVersion.longValue++
            }
            ProjectionChange.PROGRESS -> progressVersion.longValue++
            ProjectionChange.DETAIL -> Unit
        }
        return LibraryStore.save(context, mutableRecords)
    }
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
        requestReferer = incoming.requestReferer ?: existing.requestReferer,
        requestOrigin = incoming.requestOrigin ?: existing.requestOrigin,
        thumbnailUrl = incoming.thumbnailUrl ?: existing.thumbnailUrl,
        pageTitle = incoming.pageTitle ?: existing.pageTitle,
        videoName = incoming.videoName ?: existing.videoName,
        folderId = incoming.folderId ?: existing.folderId,
        manualFolder = incoming.manualFolder || existing.manualFolder,
        explicitlySaved = incoming.explicitlySaved || existing.explicitlySaved,
        qualityHeight = incoming.qualityHeight ?: existing.qualityHeight,
        selectedSubtitle = incoming.selectedSubtitle ?: existing.selectedSubtitle,
        hlsRenditions = incoming.hlsRenditions.ifEmpty { existing.hlsRenditions },
        // The handoff's actual start point is authoritative: opening from a Library card passes
        // its resume point, while intentionally starting the same URL elsewhere passes zero and
        // correctly resets the watched-progress bar instead of leaving stale progress visible.
        positionMs = incoming.positionMs.coerceAtLeast(0L),
        durationMs = incoming.durationMs.takeIf { it > 0L } ?: existing.durationMs
    )
}
