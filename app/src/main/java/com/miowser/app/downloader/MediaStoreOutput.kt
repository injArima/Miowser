package com.miowser.app.downloader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream

private const val TAG = "Miowser/Downloader"

/**
 * Writes finished downloads into the public Downloads collection under a "Miowser" subfolder —
 * visible in the user's file manager / Downloads app / other apps, not hidden in this app's
 * private storage, which is the whole point of a downloader. Uses MediaStore.Downloads (API 29+)
 * since scoped storage forbids direct File access to public directories on this project's
 * targetSdk 37.
 *
 * minSdk is 26, so pre-Q devices fall back to a direct File write into the legacy public
 * Downloads dir. Two things the Q+ path gets for free that the legacy path has to do itself:
 * 1. WRITE_EXTERNAL_STORAGE is a dangerous permission on API 23+ — declaring it in the manifest
 *    (maxSdkVersion 28) isn't suffient on its own; without an actual grant, mkdirs()/
 *    outputStream() throw a SecurityException. [openViaLegacyFile] checks first and fails
 *    cleanly (a normal "could not create output file", not a crash) if it's missing —
 *    MainActivity requests it proactively so this should be the rare path in practice.
 * 2. A file written directly to external storage isn't visible to MediaStore / other apps'
 *    pickers / the Downloads app until something scans it — unlike the Q+ path, where inserting
 *    through ContentResolver makes it visible automatically. [finish] triggers a
 *    MediaScannerConnection scan on the legacy path for exactly this reason.
 *
 * Still genuinely unverified on real pre-Q hardware — no such device or emulator image was
 * available in this environment — but both gaps above were real, findable defects independent of
 * that: a real API 26-28 device would have hit the missing-permission crash on first download
 * before it ever reached the "is my file visible" question.
 */
object MediaStoreOutput {
    data class Opened(val uri: Uri, val stream: OutputStream)

    fun open(context: Context, fileName: String, mimeType: String): Opened? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openViaMediaStore(context, fileName, mimeType)
        } else {
            openViaLegacyFile(context, fileName)
        }
    }

    /**
     * Marks the entry no-longer-pending so it becomes visible to other apps (Q+), or triggers a
     * media scan so it becomes visible at all (pre-Q — see class doc comment). [mimeType] is only
     * used on the pre-Q path; Q+ already recorded it at insert time.
     */
    fun finish(context: Context, uri: Uri, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            uri.path?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
            }
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

    private fun openViaLegacyFile(context: Context, fileName: String): Opened? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE_EXTERNAL_STORAGE not granted; can't write $fileName on this pre-Q device")
            return null
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Miowser")
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        return Opened(Uri.fromFile(file), file.outputStream())
    }
}
