package com.miowser.app.library.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

internal enum class DeviceMediaAccess { NONE, SELECTED, FULL }

internal fun deviceVideoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun deviceMediaAccess(context: Context): DeviceMediaAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val video = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    return when {
        granted(video) -> DeviceMediaAccess.FULL
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> DeviceMediaAccess.SELECTED
        else -> DeviceMediaAccess.NONE
    }
}

internal fun mountedVideoVolumes(context: Context): Set<String> =
    if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else setOf("external")

internal data class MediaVolumeStamp(val version: String, val generation: Long) {
    val key get() = "$version:$generation"
}

internal fun videoVolumeStamp(context: Context, volume: String): MediaVolumeStamp? =
    if (Build.VERSION.SDK_INT >= 30)
        MediaVolumeStamp(MediaStore.getVersion(context, volume), MediaStore.getGeneration(context, volume))
    else null

internal fun scanMediaVolume(
    context: Context,
    volume: String,
    source: String,
    generation: String,
    stamp: MediaVolumeStamp?,
    signal: CancellationSignal,
    emit: (DeviceVideo) -> Unit
) {
    val collection = MediaStore.Video.Media.getContentUri(volume)
    val base = arrayOf("_id", "_display_name", "duration", "height", "date_modified", "_size")
    val projection = base +
        (if (Build.VERSION.SDK_INT >= 29) arrayOf("relative_path") else arrayOf("_data")) +
        (if (Build.VERSION.SDK_INT >= 30) arrayOf("generation_modified") else emptyArray())
    val restrictions = buildList {
        if (Build.VERSION.SDK_INT >= 29) add("is_pending = 0")
        if (Build.VERSION.SDK_INT >= 30) add("is_trashed = 0")
    }.joinToString(" AND ").ifBlank { null }
    val cursor = checkNotNull(context.contentResolver.query(
        collection, projection, restrictions, null, null, signal
    )) { "MediaStore did not return a video inventory" }
    cursor.use {
        while (it.moveToNext()) {
            signal.throwIfCanceled()
            val uri = ContentUris.withAppendedId(collection, it.getLong(0)).toString()
            val title = it.getString(1)?.takeIf(String::isNotBlank) ?: "Untitled video"
            val modified = it.getLong(4)
            val revision = if (Build.VERSION.SDK_INT >= 30) "media:${stamp?.version}:${it.getLong(7)}"
                else "${modified * 1_000}:${it.getLong(5)}"
            val path = it.getString(6).orEmpty()
            val folder = if (Build.VERSION.SDK_INT >= 29) path.trimEnd('/') else path.substringBeforeLast('/', "")
            emit(DeviceVideo(source, generation, uri, uri, DEVICE_MEDIA, title,
                folder, it.getLong(2).coerceAtLeast(0),
                it.getInt(3).takeIf { height -> height > 0 }, modified * 1_000, revision))
        }
    }
}

internal fun linkedTree(uri: Uri): Uri? = runCatching {
    if (!DocumentsContract.isTreeUri(uri)) null
    else DocumentsContract.buildTreeDocumentUri(requireNotNull(uri.authority), DocumentsContract.getTreeDocumentId(uri))
}.getOrNull()

/** A shared-storage tree is covered by the MediaStore inventory for its mounted volume. */
internal fun linkedStorageVolume(sourceId: String): String? {
    val uri = Uri.parse(sourceId)
    if (uri.authority != "com.android.externalstorage.documents") return null
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volume = id.substringBefore(':', "")
    return when {
        volume.equals("primary", ignoreCase = true) ->
            if (Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
        else -> volume.lowercase().ifEmpty { null }
    }
}

internal fun canonicalVideoUri(context: Context, uri: Uri): String {
    val candidate = if (Build.VERSION.SDK_INT >= 29 && uri.authority in setOf(
            "com.android.externalstorage.documents", "com.android.providers.downloads.documents",
            "com.android.providers.media.documents"
        )) {
        runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() ?: return uri.toString()
    } else uri
    if (candidate.authority != "media") return candidate.toString()
    return runCatching {
        val id = ContentUris.parseId(candidate)
        val volume = if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.query(candidate, arrayOf("volume_name"), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: return candidate.toString()
        } else "external"
        ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(volume), id).toString()
    }.getOrDefault(candidate.toString())
}

internal data class DeviceDownload(val uri: Uri, val title: String)

internal fun mediaArtworkRevision(context: Context, canonical: Uri): String? {
    if (Build.VERSION.SDK_INT < 30 || canonical.authority != "media") return null
    val volume = canonical.pathSegments.firstOrNull() ?: return null
    return runCatching {
        context.contentResolver.query(canonical, arrayOf("generation_modified"), null, null, null)?.use {
            if (it.moveToFirst()) "media:${MediaStore.getVersion(context, volume)}:${it.getLong(0)}" else null
        }
    }.getOrNull()
}

internal fun localArtworkRevision(context: Context, uri: Uri, canonical: Uri): String? =
    mediaArtworkRevision(context, canonical) ?: when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.let { "${it.lastModified()}:${it.length()}" }
        "content" -> runCatching {
            val columns = if (uri.authority == "media") arrayOf("date_modified", "_size")
                else arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE)
            context.contentResolver.query(uri, columns, null, null, null)?.use {
                if (!it.moveToFirst() || it.columnCount < 2) return@use null
                val modified = it.getLong(0) * if (uri.authority == "media") 1_000 else 1
                "$modified:${it.getLong(1)}"
            }
        }.getOrNull()
        else -> null
    }

internal fun readDeviceDownload(context: Context, download: DeviceDownload, generation: String): DeviceVideo? {
    val uri = download.uri
    val file = uri.path?.let(::File).takeIf { uri.scheme == "file" }
    val version = if (file != null) {
        if (!file.isFile || !file.canRead()) return null
        file.lastModified() to file.length()
    } else {
        if (uri.scheme != "content") return null
        val resolver = context.contentResolver
        val modified = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val media = cursor.getColumnIndex("date_modified")
                val document = cursor.getColumnIndex("last_modified")
                when {
                    media >= 0 -> cursor.getLong(media) * 1_000
                    document >= 0 -> cursor.getLong(document)
                    else -> 0L
                }
            } ?: 0L
        }.getOrDefault(0L)
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: return null
        modified to size
    }
    return DeviceVideo(DOWNLOAD_SOURCE, generation, uri.toString(), canonicalVideoUri(context, uri),
        DEVICE_DOWNLOADS, download.title, "Downloads", 0, null, version.first, "${version.first}:${version.second}")
}
