package com.miowser.app.library

import android.content.Context

/**
 * Checks only local entries. A remote stream cannot be called unavailable without actually
 * attempting network playback: signed URLs, cookies, redirects, and temporary outages make a
 * lightweight preflight actively misleading.
 */
fun isLibraryEntryAvailable(context: Context, entry: LibraryEntry, force: Boolean = false): Boolean {
    if (entry.sourceKind == LibrarySourceKind.REMOTE_STREAM) return true
    return LocalInspectionCache.available(context, entry.mediaUri, force)
}
