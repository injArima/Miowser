package com.miowser.app.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/** Resolves old .bin/opaque-name downloads from their saved bytes, not their display name. */
object DownloadedFileInspector {
    private val resolved = ConcurrentHashMap<String, DownloadedFileType>()

    fun cachedOrBasic(download: Download): DownloadedFileType? {
        val uri = download.savedUri?.toString() ?: return null
        resolved[uri]?.let { return it }
        val basic = downloadedFileType(download.displayName, null, null, download.kind, download.sourceUrl)
        return basic.takeUnless {
            it == DownloadedFileType.OTHER &&
                download.displayName.substringAfterLast('.', "").lowercase() in setOf("bin", "tmp", "file", "download", "")
        }
    }

    /** Call off the main thread: content providers and metadata extraction can block. */
    fun inspect(context: Context, download: Download): DownloadedFileType {
        val uri = download.savedUri ?: return DownloadedFileType.OTHER
        val key = uri.toString()
        resolved[key]?.let { return it }
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val signature = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                val count = input.read(buffer)
                if (count > 0) DownloadIdentityResolver.sniffExtension(buffer.copyOf(count)) else null
            }
        }.getOrNull()
        var type = downloadedFileType(download.displayName, mimeType, signature, download.kind, download.sourceUrl)
        // Some older downloads have neither a useful name, MIME type nor a recognizable prefix.
        // The platform extractor can still identify containers whose header is further in.
        if (type == DownloadedFileType.OTHER && signature == null) {
            val hasMedia = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                } finally {
                    retriever.release()
                }
            }.getOrDefault(false)
            if (hasMedia) type = DownloadedFileType.MEDIA
        }
        resolved[key] = type
        return type
    }

    fun forget(uri: Uri?) {
        uri?.toString()?.let(resolved::remove)
    }
}
