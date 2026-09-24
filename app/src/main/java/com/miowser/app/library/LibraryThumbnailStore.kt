package com.miowser.app.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

private const val THUMBNAIL_WIDTH = 640
private const val THUMBNAIL_HEIGHT = 360
private const val MAX_CAPTURE_EDGE = 1_280
private const val JPEG_QUALITY = 86
private const val THUMBNAIL_DIRECTORY = "library_thumbnails"
private const val DOWNLOAD_THUMBNAIL_DIRECTORY = "download_thumbnails"
private const val THUMBNAIL_CACHE_VERSION = "v3"
private const val MEMORY_CACHE_KIB = 24 * 1024

/** Captures and owns persistent artwork generated from Media3's already-decoded video frame. */
object LibraryThumbnailStore {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturesInFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val downloadThumbnailLock = Any()

    fun peek(thumbnailUri: String?): Bitmap? = thumbnailUri?.let(memoryCache::get)

    fun isMiddleFrame(thumbnailUri: String?): Boolean =
        isAvailable(thumbnailUri) && thumbnailUri
            ?.substringAfterLast('/')?.startsWith("${THUMBNAIL_CACHE_VERSION}_middle_") == true

    /**
     * Fallback for streams that cannot be independently sought by the platform extractor.
     */
    fun captureFirstFrame(
        context: Context,
        playerView: PlayerView,
        identityKey: String,
        middle: Boolean = false,
        onComplete: (String?) -> Unit
    ) {
        val captureKey = "$identityKey:${if (middle) "middle" else "fallback"}"
        if (!capturesInFlight.add(captureKey)) {
            onComplete(null)
            return
        }
        val surface = playerView.videoSurfaceView
        val width = surface?.width ?: 0
        val height = surface?.height ?: 0
        if (surface == null || width <= 0 || height <= 0) {
            capturesInFlight.remove(captureKey)
            onComplete(null)
            return
        }

        val (captureWidth, captureHeight) = boundedCaptureSize(width, height)
        val videoSize = playerView.player?.videoSize
        val sourceAspect = if (playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL &&
            videoSize != null && videoSize.width > 0 && videoSize.height > 0
        ) {
            val raw = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            if (videoSize.unappliedRotationDegrees % 180 != 0) 1f / raw else raw
        } else null
        when (surface) {
            is TextureView -> {
                val bitmap = surface.getBitmap(captureWidth, captureHeight)
                if (bitmap == null) {
                    capturesInFlight.remove(captureKey)
                    onComplete(null)
                } else {
                    storeAsync(context.applicationContext, identityKey, captureKey, bitmap, middle, sourceAspect, onComplete)
                }
            }

            is SurfaceView -> {
                val bitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
                PixelCopy.request(surface, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        storeAsync(context.applicationContext, identityKey, captureKey, bitmap, middle, sourceAspect, onComplete)
                    } else {
                        bitmap.recycle()
                        capturesInFlight.remove(captureKey)
                        onComplete(null)
                    }
                }, mainHandler)
            }

            else -> {
                capturesInFlight.remove(captureKey)
                onComplete(null)
            }
        }
    }

    /** Fetches the frame nearest the exact midpoint without moving the visible player's playhead. */
    fun captureMiddleFrame(
        context: Context,
        mediaUri: Uri,
        headers: Map<String, String>?,
        durationMs: Long,
        identityKey: String,
        onComplete: (String?) -> Unit
    ) {
        val captureKey = "$identityKey:middle"
        if (!capturesInFlight.add(captureKey)) {
            onComplete(null)
            return
        }
        executor.execute {
            val frame = extractFrame(context.applicationContext, mediaUri, headers, durationMs.takeIf { it > 0L })
            if (frame == null) {
                capturesInFlight.remove(captureKey)
                mainHandler.post { onComplete(null) }
            } else {
                storeAsync(context.applicationContext, identityKey, captureKey, frame, true, null, onComplete)
            }
        }
    }

    /** A separate persistent folder covers completed downloads before they enter the Library. */
    fun existingDownloadedThumbnail(context: Context, mediaUri: Uri): String? {
        val directory = File(context.filesDir, DOWNLOAD_THUMBNAIL_DIRECTORY)
        val target = File(directory, thumbnailCacheFileName("DOWNLOAD:$mediaUri", middle = true))
        return target.takeIf { it.isFile && it.length() > 0L }?.let(Uri::fromFile)?.toString()
    }

    fun ensureDownloadedThumbnail(context: Context, mediaUri: Uri): String? =
        synchronized(downloadThumbnailLock) {
            existingDownloadedThumbnail(context, mediaUri)?.let { return@synchronized it }
            val frame = extractFrame(context, mediaUri, null, null) ?: return@synchronized null
            val directory = File(context.filesDir, DOWNLOAD_THUMBNAIL_DIRECTORY)
            val target = File(directory, thumbnailCacheFileName("DOWNLOAD:$mediaUri", middle = true))
            val thumbnail = fitInside16By9(frame)
            try {
                runCatching {
                    check(directory.isDirectory || directory.mkdirs())
                    val temporary = File(directory, "${target.name}.tmp")
                    val saved = temporary.outputStream().buffered().use {
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                    }
                    check(saved && (temporary.renameTo(target) || run {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                        true
                    }))
                    Uri.fromFile(target).toString()
                }.getOrNull()
            } finally {
                if (thumbnail !== frame) thumbnail.recycle()
                frame.recycle()
            }
        }

    fun removeDownloadedThumbnail(context: Context, mediaUri: Uri?) {
        if (mediaUri == null) return
        executor.execute {
            val file = File(
                File(context.applicationContext.filesDir, DOWNLOAD_THUMBNAIL_DIRECTORY),
                thumbnailCacheFileName("DOWNLOAD:$mediaUri", middle = true)
            )
            memoryCache.remove(Uri.fromFile(file).toString())
            runCatching { file.delete() }
        }
    }

    fun pruneDownloadedAsync(context: Context, retainedUris: Collection<Uri>) {
        val pruneStartedAt = System.currentTimeMillis()
        val retainedNames = retainedUris.map {
            thumbnailCacheFileName("DOWNLOAD:$it", middle = true)
        }.toSet()
        executor.execute {
            File(context.applicationContext.filesDir, DOWNLOAD_THUMBNAIL_DIRECTORY)
                .listFiles()
                ?.filter { it.isFile && it.extension == "jpg" &&
                    it.name !in retainedNames && it.lastModified() < pruneStartedAt }
                ?.forEach { file ->
                    memoryCache.remove(Uri.fromFile(file).toString())
                    runCatching { file.delete() }
                }
        }
    }

    private fun extractFrame(
        context: Context,
        mediaUri: Uri,
        headers: Map<String, String>?,
        durationMs: Long?
    ): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            if (mediaUri.scheme == "http" || mediaUri.scheme == "https") {
                retriever.setDataSource(mediaUri.toString(), headers ?: emptyMap())
            } else {
                retriever.setDataSource(context, mediaUri)
            }
            val duration = durationMs ?: retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val middleUs = duration?.takeIf { it > 0L }?.let { (it / 2L) * 1_000L } ?: 0L
            retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    fun isAvailable(thumbnailUri: String?): Boolean {
        if (thumbnailUri.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(thumbnailUri) }.getOrNull() ?: return false
        return uri.scheme == "file" && uri.path?.let { path ->
            val file = File(path)
            file.isFile && file.length() > 0L && file.name.startsWith("${THUMBNAIL_CACHE_VERSION}_")
        } == true
    }

    fun load(context: Context, thumbnailUri: String): Bitmap? {
        memoryCache.get(thumbnailUri)?.let { return it }
        val decoded = runCatching {
            context.contentResolver.openInputStream(Uri.parse(thumbnailUri))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull() ?: return null
        memoryCache.put(thumbnailUri, decoded)
        return decoded
    }

    /** Removes thumbnails whose Library records no longer exist; runs only after app restore. */
    fun pruneAsync(context: Context, retainedThumbnailUris: Collection<String>) {
        val pruneStartedAt = System.currentTimeMillis()
        val retainedPaths = retainedThumbnailUris.mapNotNull { value ->
            runCatching { Uri.parse(value).path }.getOrNull()
        }.toSet()
        executor.execute {
            File(context.applicationContext.filesDir, THUMBNAIL_DIRECTORY)
                .listFiles()
                ?.filter { it.isFile && it.extension == "jpg" &&
                    it.absolutePath !in retainedPaths && it.lastModified() < pruneStartedAt }
                ?.forEach { file ->
                    memoryCache.remove(Uri.fromFile(file).toString())
                    runCatching { file.delete() }
                }
        }
    }

    private fun storeAsync(
        context: Context,
        identityKey: String,
        captureKey: String,
        captured: Bitmap,
        middle: Boolean,
        sourceAspectRatio: Float?,
        onComplete: (String?) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val thumbnail = fitInside16By9(captured, sourceAspectRatio)
                val directory = File(context.filesDir, THUMBNAIL_DIRECTORY).apply { mkdirs() }
                check(directory.isDirectory) { "Could not create thumbnail directory" }
                val target = File(directory, thumbnailCacheFileName(identityKey, middle))
                val temporary = File(directory, "${target.name}.tmp")
                val compressed = temporary.outputStream().buffered().use { output ->
                    thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                if (thumbnail !== captured) thumbnail.recycle()
                captured.recycle()
                check(compressed) { "Could not encode thumbnail" }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                Uri.fromFile(target).toString()
            }.getOrNull()
            capturesInFlight.remove(captureKey)
            mainHandler.post { onComplete(result) }
        }
    }

    private fun fitInside16By9(source: Bitmap, sourceAspectRatio: Float? = null): Bitmap {
        val output = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val aspect = sourceAspectRatio?.takeIf { it.isFinite() && it > 0f }
            ?: source.width.toFloat() / source.height.coerceAtLeast(1)
        val drawWidth = min(THUMBNAIL_WIDTH.toFloat(), THUMBNAIL_HEIGHT * aspect)
        val drawHeight = drawWidth / aspect
        val left = (THUMBNAIL_WIDTH - drawWidth) / 2f
        val top = (THUMBNAIL_HEIGHT - drawHeight) / 2f
        canvas.drawBitmap(
            source,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return output
    }
}

internal fun thumbnailCacheFileName(identityKey: String, middle: Boolean = true): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identityKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "${THUMBNAIL_CACHE_VERSION}_${if (middle) "middle" else "fallback"}_$digest.jpg"
}

private fun boundedCaptureSize(width: Int, height: Int): Pair<Int, Int> {
    if (width <= MAX_CAPTURE_EDGE && height <= MAX_CAPTURE_EDGE) return width to height
    val scale = MAX_CAPTURE_EDGE.toFloat() / maxOf(width, height)
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}
