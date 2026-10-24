package com.miowser.app.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.miowser.app.library.videoQualityHeight
import com.miowser.app.library.LocalInspectionCache

/** Resolves old .bin/opaque-name downloads from their saved bytes, not their display name. */
object DownloadedFileInspector {
    private data class Metadata(val hasMedia: Boolean, val height: Int, val retryAt: Long)
    private val locks = Array(32) { Any() }

    fun cachedOrBasic(download: Download): DownloadedFileType? {
        val uri = download.savedUri?.toString() ?: return null
        cachedType(uri)?.let { return it }
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
        cachedType(key)?.let { return it }
        return synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            cachedType(key)?.let { return@synchronized it }
            val ticket = LocalInspectionCache.ticket()
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val signatureRead = runCatching {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    val count = input.read(buffer)
                    if (count > 0) DownloadIdentityResolver.sniffExtension(buffer.copyOf(count)) else null
                }
            }
            val signature = signatureRead.getOrNull()
            var cacheLifetime = if (signatureRead.isSuccess) LocalInspectionCache.SUCCESS_MS else LocalInspectionCache.RETRY_MS
            var type = downloadedFileType(download.displayName, mimeType, signature, download.kind, download.sourceUrl)
            // Some older downloads have neither a useful name, MIME type nor a recognizable prefix.
            // The platform extractor can still identify containers whose header is further in.
            if (type == DownloadedFileType.OTHER && signature == null) {
                val info = readMetadata(context, uri)
                if (info.hasMedia) type = DownloadedFileType.MEDIA
                if (info.retryAt != Long.MAX_VALUE) cacheLifetime = LocalInspectionCache.RETRY_MS
            }
            LocalInspectionCache.remember("type", key, type.name, cacheLifetime, ticket)
            type
        }
    }

    fun forget(uri: Uri?) {
        uri?.toString()?.let { key ->
            LocalInspectionCache.forget(key)
        }
    }

    /** Call off the main thread; zero is cached for files without readable video dimensions. */
    fun videoQualityHeight(context: Context, uri: Uri): Int? =
        readMetadata(context, uri).height.takeIf { it > 0 }

    fun cachedQualityHeight(uri: String?): Int? = uri?.let(::cachedMetadata)?.height?.takeIf { it > 0 }
    private fun cachedType(uri: String): DownloadedFileType? = LocalInspectionCache.peek("type", uri)
        ?.let { runCatching { DownloadedFileType.valueOf(it) }.getOrNull() }
    private fun cachedMetadata(uri: String): Metadata? = LocalInspectionCache.peek("metadata", uri)?.let {
        runCatching {
            val fields = it.split(':')
            Metadata(fields[0].toBooleanStrict(), fields[1].toInt().also { height -> require(height >= 0) },
                if (fields.getOrNull(2) == "true") Long.MAX_VALUE else 0L)
        }.getOrNull()
    }

    private fun readMetadata(context: Context, uri: Uri): Metadata {
        val key = uri.toString()
        cachedMetadata(key)?.let { return it }
        return synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            cachedMetadata(key)?.let { return@synchronized it }
            val ticket = LocalInspectionCache.ticket()
            val result = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    Metadata(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                        videoQualityHeight(
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        ) ?: 0,
                        Long.MAX_VALUE
                    )
                } finally { retriever.release() }
            }.getOrElse { Metadata(false, 0, System.currentTimeMillis() + 5 * 60_000) }
            LocalInspectionCache.remember("metadata", key, "${result.hasMedia}:${result.height}:${result.retryAt == Long.MAX_VALUE}",
                if (result.retryAt == Long.MAX_VALUE) LocalInspectionCache.SUCCESS_MS else LocalInspectionCache.RETRY_MS,
                ticket)
            result
        }
    }
}
