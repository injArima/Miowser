package com.miowser.app.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Writes finished downloads into the public Downloads collection under a "Miowser" subfolder —
 * visible in the user's file manager / Downloads app / other apps, not hidden in this app's
 * private storage, which is the whole point of a downloader. Uses MediaStore.Downloads (API 29+)
 * since scoped storage forbids direct File access to public directories on this project's
 * targetSdk 37.
 *
 * minSdk is 26, so pre-Q devices fall back to a direct File write into the legacy public
 * Downloads dir (needs WRITE_EXTERNAL_STORAGE, declared with maxSdkVersion so it's a no-op on
 * modern Android). This fallback path is untested — there's no pre-Q device available for
 * on-device verification here — flagged as a known gap rather than assumed correct.
 */
object MediaStoreOutput {
    data class Opened(val uri: Uri, val stream: OutputStream)

    fun open(context: Context, fileName: String, mimeType: String): Opened? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openViaMediaStore(context, fileName, mimeType)
        } else {
            openViaLegacyFile(fileName)
        }
    }

    /** Marks the entry no-longer-pending so it becomes visible to other apps. Q+ only; a no-op below Q. */
    fun finish(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
    }

    /** Deletes a partial/canceled/failed download's output rather than leaving a pending ghost entry. */
    fun abandon(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(uri, null, null)
        } else {
            uri.path?.let { File(it).delete() }
        }
    }

    private fun openViaMediaStore(context: Context, fileName: String, mimeType: String): Opened? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Miowser")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val stream = context.contentResolver.openOutputStream(uri) ?: return null
        return Opened(uri, stream)
    }

    private fun openViaLegacyFile(fileName: String): Opened? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Miowser")
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        return Opened(Uri.fromFile(file), file.outputStream())
    }
}
