package com.miowser.app.library.device

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miowser.app.library.isImportableVideo
import com.miowser.app.player.PlaybackRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single Android URI grant is sufficient. This path never queries the device catalog. */
internal suspend fun openGrantedVideo(context: Context, uri: Uri): PlaybackRequest.Local? =
    withContext(Dispatchers.IO) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank() ||
            uri.authority == "${context.packageName}.fileprovider") return@withContext null
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()?.trim()?.takeIf(String::isNotBlank)
        val genericMime = mime.isEmpty() || mime.equals("application/octet-stream", ignoreCase = true)
        if (!mime.startsWith("video/", ignoreCase = true) &&
            !(genericMime && isImportableVideo(displayName.orEmpty(), ""))) return@withContext null
        val readable = runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } == true }.getOrDefault(false)
        if (!readable) return@withContext null
        val title = displayName ?: "Video"
        PlaybackRequest.Local(uri, title, "Opened file", videoName = title)
    }
