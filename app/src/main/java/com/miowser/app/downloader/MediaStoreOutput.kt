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
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

private const val TAG = "Miowser/Downloader"

/**
 * Writes finished downloads to the user's configured public destination. The default is the
 * Downloads collection under a "Miowser" subfolder; a custom folder uses its persisted Storage
 * Access Framework tree grant. Files remain visible in file managers and other apps rather than
 * being hidden in app-private storage. The default path uses MediaStore.Downloads (API 29+), since
 * scoped storage forbids direct File access to public directories on this project's targetSdk 37.
 *
 * minSdk is 26, so pre-Q devices fall back to a direct File write into the legacy public
 * Downloads dir. Two things the Q+ path gets for free that the legacy path has to do itself:
 * 1. WRITE_EXTERNAL_STORAGE is a dangerous permission on API 23+ — declaring it in the manifest
 *    (maxSdkVersion 28) isn't suffient on its own; without an actual grant, mkdirs()/
 *    outputStream() throw a SecurityException. [openViaLegacyFile] checks first and fails
 *    cleanly (a normal "could not create output file", not a crash) if it's missing —
 *    Browser download actions request it before starting on API 26–28.
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

    /** MediaStore may choose its own name; the legacy path returns the exact reserved filename. */
    fun legacyFileName(uri: Uri): String? =
        uri.takeIf { it.scheme == "file" }?.path?.let { File(it).name }

    fun actualFileName(context: Context, uri: Uri): String? {
        legacyFileName(uri)?.let { return it }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    /**
     * A position-addressable handle on the same output location [open] would give a plain
     * OutputStream — for the multi-threaded downloader, where several threads write different
     * byte ranges of the same file concurrently via [FileChannel.write] (its own positioned
     * overload, safe to call from multiple threads on one channel without external
     * synchronization; each call is atomic against the given position, unlike a shared
     * seek()+write() pair). [close] releases everything this needed to open, not just the
     * channel itself — on the Q+ path that's a ParcelFileDescriptor the channel's own close()
     * never touches.
     */
    class OpenedRandomAccess(val uri: Uri, val channel: FileChannel, private val toClose: List<Closeable>) : Closeable {
        override fun close() {
            toClose.forEach { runCatching { it.close() } }
        }
    }

    fun open(context: Context, fileName: String, mimeType: String): Opened? {
        if (!OutputFileIdentity.validFileName(fileName) || !OutputFileIdentity.validMimeType(mimeType)) return null
        if (DownloadDirectoryStore.isCustom(context)) return openViaDocumentTree(context, fileName, mimeType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openViaMediaStore(context, fileName, mimeType)
        } else {
            openViaLegacyFile(context, fileName)
        }
    }

    fun openRandomAccess(context: Context, fileName: String, mimeType: String): OpenedRandomAccess? {
        if (!OutputFileIdentity.validFileName(fileName) || !OutputFileIdentity.validMimeType(mimeType)) return null
        if (DownloadDirectoryStore.isCustom(context)) return openRandomAccessViaDocumentTree(context, fileName, mimeType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openRandomAccessViaMediaStore(context, fileName, mimeType)
        } else {
            openRandomAccessViaLegacyFile(context, fileName)
        }
    }

    /** Publishes a completely-written private recovery artifact as one atomic user-visible result. */
    fun publishFile(context: Context, source: File, fileName: String, mimeType: String, checkActive: () -> Unit = {}): Uri? {
        if (!source.isFile) return null
        val opened = open(context, fileName, mimeType) ?: return null
        return try {
            source.inputStream().use { input -> opened.stream.use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            } }
            checkActive()
            finish(context, opened.uri, mimeType)
            opened.uri
        } catch (e: Exception) {
            runCatching { opened.stream.close() }
            abandon(context, opened.uri)
            if (e is DownloadYieldException || e is java.util.concurrent.CancellationException) throw e
            null
        }
    }

    /**
     * Marks the entry no-longer-pending so it becomes visible to other apps (Q+), or triggers a
     * media scan so it becomes visible at all (pre-Q — see class doc comment). [mimeType] is only
     * used on the pre-Q path; Q+ already recorded it at insert time.
     */
    fun finish(context: Context, uri: Uri, mimeType: String) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // SAF documents are visible as soon as their stream closes.
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            uri.path?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
            }
        }
    }

    /** Deletes a partial/canceled/failed download's output rather than leaving a pending ghost entry. */
    fun abandon(context: Context, uri: Uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(uri, null, null)
        } else {
            uri.path?.let { path ->
                if (File(path).delete()) {
                    // Remove a previously scanned completed file from the legacy MediaStore index.
                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
            }
        }
    }

    /** Deletes a finished public file when the user explicitly chooses "File and record". */
    fun delete(context: Context, uri: Uri) = abandon(context, uri)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertMediaStoreEntry(context: Context, fileName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Miowser")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openViaMediaStore(context: Context, fileName: String, mimeType: String): Opened? {
        val uri = insertMediaStoreEntry(context, fileName, mimeType) ?: return null
        return try {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: run { context.contentResolver.delete(uri, null, null); return null }
            Opened(uri, stream)
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun legacyOutputDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Miowser")

    private fun reserveLegacyFile(fileName: String): File? {
        if (fileName.isBlank() || fileName == "." || fileName == ".." ||
            '/' in fileName || '\\' in fileName
        ) return null
        val dir = legacyOutputDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        // createNewFile is atomic: two downloads cannot silently choose and overwrite the same
        // public file even when it was created outside Miowser and absent from our download list.
        for (copyNumber in 1..10_000) {
            val candidate = File(dir, legacyOutputName(fileName, copyNumber))
            if (candidate.createNewFile()) return candidate
        }
        return null
    }

    private fun openViaLegacyFile(context: Context, fileName: String): Opened? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "WRITE_EXTERNAL_STORAGE not granted; can't write $fileName on this pre-Q device")
            return null
        }
        val file = reserveLegacyFile(fileName) ?: return null
        return try {
            Opened(Uri.fromFile(file), file.outputStream())
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun openViaDocumentTree(context: Context, fileName: String, mimeType: String): Opened? {
        val uri = DownloadDirectoryStore.createCustomDocument(context, fileName, mimeType) ?: return null
        return try {
            val stream = context.contentResolver.openOutputStream(uri, "w")
                ?: run { DocumentsContract.deleteDocument(context.contentResolver, uri); return null }
            Opened(uri, stream)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }

    // "rw" (not "rwt"): mode is passed straight to ContentResolver.openFileDescriptor, which
    // treats it as the standard ParcelFileDescriptor mode string — "rwt" would truncate on open,
    // fighting every thread that opens its own descriptor onto the same already-inserted entry.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openRandomAccessViaMediaStore(context: Context, fileName: String, mimeType: String): OpenedRandomAccess? {
        val uri = insertMediaStoreEntry(context, fileName, mimeType) ?: return null
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: run { context.contentResolver.delete(uri, null, null); return null }
            val stream = FileOutputStream(pfd.fileDescriptor)
            OpenedRandomAccess(uri, stream.channel, listOf(stream, pfd))
        } catch (e: Exception) {
            runCatching { pfd?.close() }
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun openRandomAccessViaLegacyFile(context: Context, fileName: String): OpenedRandomAccess? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "WRITE_EXTERNAL_STORAGE not granted; can't write $fileName on this pre-Q device")
            return null
        }
        val file = reserveLegacyFile(fileName) ?: return null
        return try {
            val raf = RandomAccessFile(file, "rw")
            OpenedRandomAccess(Uri.fromFile(file), raf.channel, listOf(raf))
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun openRandomAccessViaDocumentTree(
        context: Context,
        fileName: String,
        mimeType: String
    ): OpenedRandomAccess? {
        val uri = DownloadDirectoryStore.createCustomDocument(context, fileName, mimeType) ?: return null
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: run { DocumentsContract.deleteDocument(context.contentResolver, uri); return null }
            val stream = FileOutputStream(pfd.fileDescriptor)
            OpenedRandomAccess(uri, stream.channel, listOf(stream, pfd))
        } catch (error: Exception) {
            runCatching { pfd?.close() }
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }
}
