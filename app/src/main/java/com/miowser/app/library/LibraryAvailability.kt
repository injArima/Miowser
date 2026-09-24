package com.miowser.app.library

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Checks only local entries. A remote stream cannot be called unavailable without actually
 * attempting network playback: signed URLs, cookies, redirects, and temporary outages make a
 * lightweight preflight actively misleading.
 */
fun isLibraryEntryAvailable(context: Context, entry: LibraryEntry): Boolean {
    if (entry.sourceKind == LibrarySourceKind.REMOTE_STREAM) return true
    return runCatching {
        val uri = Uri.parse(entry.mediaUri)
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).isFile && File(it).canRead() } == true
            "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            else -> false
        }
    }.getOrDefault(false)
}
