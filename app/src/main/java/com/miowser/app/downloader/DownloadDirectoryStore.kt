package com.miowser.app.downloader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

private const val DOWNLOAD_PREFS = "downloads"
private const val KEY_DIRECTORY_MODE = "directory_mode"
private const val KEY_DIRECTORY_URI = "directory_uri"
private const val KEY_DIRECTORY_LABEL = "directory_label"
private const val DEFAULT_DIRECTORY_LABEL = "Downloads/Miowser"

enum class DownloadDirectoryMode { DEFAULT, CUSTOM }

data class DownloadDirectory(
    val mode: DownloadDirectoryMode,
    val treeUri: Uri? = null,
    val label: String = DEFAULT_DIRECTORY_LABEL
)

/** Durable download destination selection. These keys share the backed-up downloads store. */
object DownloadDirectoryStore {
    fun load(context: Context): DownloadDirectory? {
        val prefs = context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_DIRECTORY_MODE, null)?.let {
            runCatching { DownloadDirectoryMode.valueOf(it) }.getOrNull()
        } ?: return null
        val uri = prefs.getString(KEY_DIRECTORY_URI, null)?.let(Uri::parse)
        if (mode == DownloadDirectoryMode.CUSTOM && uri == null) return null
        return DownloadDirectory(
            mode = mode,
            treeUri = uri,
            label = prefs.getString(KEY_DIRECTORY_LABEL, null).orEmpty()
                .ifBlank { if (mode == DownloadDirectoryMode.DEFAULT) DEFAULT_DIRECTORY_LABEL else "Custom folder" }
        )
    }

    fun useDefault(context: Context): DownloadDirectory {
        val value = DownloadDirectory(DownloadDirectoryMode.DEFAULT)
        check(context.applicationContext.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIRECTORY_MODE, value.mode.name)
            .remove(KEY_DIRECTORY_URI)
            .putString(KEY_DIRECTORY_LABEL, value.label)
            .commit())
        return value
    }

    fun useCustom(context: Context, treeUri: Uri): DownloadDirectory {
        val label = treeDisplayName(context, treeUri) ?: "Custom folder"
        val value = DownloadDirectory(DownloadDirectoryMode.CUSTOM, treeUri, label)
        check(context.applicationContext.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIRECTORY_MODE, value.mode.name)
            .putString(KEY_DIRECTORY_URI, treeUri.toString())
            .putString(KEY_DIRECTORY_LABEL, value.label)
            .commit())
        return value
    }

    fun hasCustomAccess(context: Context): Boolean =
        load(context)?.let { hasCustomAccess(context, it) } ?: false

    fun hasCustomAccess(context: Context, directory: DownloadDirectory): Boolean {
        if (directory.mode != DownloadDirectoryMode.CUSTOM) return true
        val expected = directory.treeUri ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == expected && it.isReadPermission && it.isWritePermission
        }
    }

    fun isCustom(context: Context): Boolean = load(context)?.mode == DownloadDirectoryMode.CUSTOM

    @Synchronized
    fun createCustomDocument(context: Context, fileName: String, mimeType: String): Uri? {
        val directory = load(context)?.takeIf { it.mode == DownloadDirectoryMode.CUSTOM } ?: return null
        val tree = directory.treeUri ?: return null
        if (!hasCustomAccess(context, directory)) return null
        val existing = customChildren(context, tree).map { it.first.lowercase() }.toHashSet()
        val candidate = (1..10_000).asSequence().map { legacyOutputName(fileName, it) }
            .firstOrNull { it.lowercase() !in existing } ?: return null
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver, root, mimeType, candidate)
    }

    fun availableFiles(context: Context): Map<String, Uri> =
        load(context)?.let { availableFiles(context, it) }.orEmpty()

    fun availableFiles(context: Context, directory: DownloadDirectory): Map<String, Uri> =
        when (directory.mode) {
            DownloadDirectoryMode.DEFAULT -> defaultFiles(context)
            DownloadDirectoryMode.CUSTOM -> {
                val tree = directory.treeUri ?: return emptyMap()
                if (!hasCustomAccess(context, directory)) emptyMap()
                else customChildren(context, tree).associate { (name, uri) -> name.lowercase() to uri }
            }
        }

    private fun defaultFiles(context: Context): Map<String, Uri> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val result = linkedMapOf<String, Uri>()
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Miowser/"
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    result.putIfAbsent(cursor.getString(name).lowercase(),
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(id)))
                }
            }
            return result
        }
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Miowser")
        return directory.listFiles()?.filter(File::isFile)
            ?.associate { it.name.lowercase() to Uri.fromFile(it) }.orEmpty()
    }

    private fun customChildren(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
        val result = mutableListOf<Pair<String, Uri>>()
        resolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                result += cursor.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return result
    }

    private fun treeDisplayName(context: Context, tree: Uri): String? {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return context.contentResolver.query(root,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
