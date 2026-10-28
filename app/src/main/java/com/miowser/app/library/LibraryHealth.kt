package com.miowser.app.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryDuplicateGroup(
    val identityKey: String,
    val entries: List<LibraryEntry>
)

data class LibraryHealthReport(
    val scannedEntries: Int,
    val missingLocalFiles: List<LibraryEntry>,
    val inaccessibleStorageEntries: List<LibraryEntry>,
    val brokenThumbnailEntries: List<LibraryEntry>,
    val orphanedFolderEntries: List<LibraryEntry>,
    val duplicateGroups: List<LibraryDuplicateGroup>,
    val remoteEntriesWithoutSourcePage: List<LibraryEntry>,
    val recentRemoteFailures: List<FailedVideoRecord>
) {
    val issueCount: Int get() = missingLocalFiles.size + inaccessibleStorageEntries.size +
        brokenThumbnailEntries.size + orphanedFolderEntries.size +
        duplicateGroups.sumOf { (it.entries.size - 1).coerceAtLeast(0) } +
        remoteEntriesWithoutSourcePage.size + recentRemoteFailures.size
}

/**
 * A deliberate local audit. Remote streams are not probed: signed URLs, cookies and redirects
 * make a HEAD/GET preflight both expensive and inaccurate. A remote source appears here only
 * after real playback has recorded a failure.
 */
object LibraryHealthScanner {
    suspend fun scan(
        context: Context,
        entries: List<LibraryEntry>,
        folders: List<LibraryFolder>
    ): LibraryHealthReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val unavailable = entries.asSequence()
            .filter { it.sourceKind == LibrarySourceKind.LOCAL_FILE }
            .filterNot { isLibraryEntryAvailable(app, it, force = true) }
            .toList()
        val inaccessibleStorage = unavailable.filter {
            runCatching { Uri.parse(it.mediaUri).scheme.equals("content", ignoreCase = true) }
                .getOrDefault(false)
        }
        val inaccessibleKeys = inaccessibleStorage.mapTo(HashSet(), LibraryEntry::identityKey)
        val missingFiles = unavailable.filterNot { it.identityKey in inaccessibleKeys }
        val validFolders = folders.mapTo(HashSet()) { it.id }
        val duplicates = entries.groupBy(LibraryEntry::identityKey)
            .filterValues { it.size > 1 }
            .map { (key, values) -> LibraryDuplicateGroup(key, values) }
            .sortedByDescending { it.entries.size }

        LibraryHealthReport(
            scannedEntries = entries.size,
            missingLocalFiles = missingFiles,
            inaccessibleStorageEntries = inaccessibleStorage,
            brokenThumbnailEntries = entries.filter { entry ->
                entry.thumbnailUrl != null && !LibraryThumbnailStore.isAvailable(entry.thumbnailUrl)
            },
            orphanedFolderEntries = entries.filter { it.folderId != null && it.folderId !in validFolders },
            duplicateGroups = duplicates,
            remoteEntriesWithoutSourcePage = entries.filter {
                it.sourceKind == LibrarySourceKind.REMOTE_STREAM && it.pageUrl.isNullOrBlank()
            },
            recentRemoteFailures = FailedVideoLog.load(app)
        )
    }
}
