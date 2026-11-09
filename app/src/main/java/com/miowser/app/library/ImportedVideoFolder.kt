package com.miowser.app.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.miowser.app.performance.traceSection
import java.util.ArrayDeque

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "mpeg", "mpg")

/** A one-time, read-only scan of a user-selected Storage Access Framework folder. */
internal data class ImportedVideoFile(
    val relativeFolder: List<String>,
    val name: String,
    val uri: Uri,
    val size: Long = 0,
    val modifiedAt: Long = 0
)

internal data class ImportedVideoFolder(
    val name: String,
    val files: List<ImportedVideoFile>
) {
    val folderPaths: List<List<String>> get() = files.asSequence().flatMap { file ->
        (0..file.relativeFolder.size).asSequence().map { file.relativeFolder.take(it) }
    }.distinct().sortedWith(compareBy<List<String>> { it.size }.thenBy { it.joinToString("/") }).toList()
}

internal fun scanImportedVideoFolder(context: Context, treeUri: Uri): ImportedVideoFolder =
    traceSection("LibraryFolderScan") {
        val files = mutableListOf<ImportedVideoFile>()
        val name = walkImportedVideoFolder(context, treeUri) { files.add(it) }
        ImportedVideoFolder(name, files)
    }

internal fun walkImportedVideoFolder(
    context: Context,
    treeUri: Uri,
    includeMetadata: Boolean = false,
    signal: android.os.CancellationSignal? = null,
    onVideo: (ImportedVideoFile) -> Unit
): String {
    val resolver = context.contentResolver
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    val name = resolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null, signal)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.trim()?.takeIf(String::isNotEmpty)
    if (includeMetadata) checkNotNull(name) { "The linked folder could not be opened" }
    val pending = ArrayDeque<Pair<String, List<String>>>()
    val visited = mutableSetOf<String>()
    pending.add(rootId to emptyList())
    val baseColumns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    val columns = if (includeMetadata) baseColumns + arrayOf(
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
    ) else baseColumns
    while (pending.isNotEmpty()) {
        signal?.throwIfCanceled()
        val (folderId, path) = pending.removeFirst()
        if (!visited.add(folderId)) continue
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        resolver.query(childrenUri, columns, null, null, null, signal)?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                signal?.throwIfCanceled()
                val childId = cursor.getString(0) ?: continue
                val childName = cursor.getString(1)?.trim().orEmpty()
                val mime = cursor.getString(2).orEmpty()
                if (childName.isBlank()) continue
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    pending.add(childId to (path + childName))
                } else if (isImportableVideo(childName, mime)) {
                    onVideo(ImportedVideoFile(path, childName,
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                        if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0,
                        if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0))
                }
            }
        } ?: error("The selected folder could not be read")
    }
    return name ?: "Imported videos"
}

internal fun isImportableVideo(name: String, mimeType: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS ||
        mimeType.startsWith("video/", ignoreCase = true)
}
