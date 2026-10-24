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
import android.os.Build
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
import kotlinx.coroutines.asCoroutineDispatcher
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
    private val executor = Executors.newFixedThreadPool(2) { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "artwork-extraction")
    } as java.util.concurrent.ThreadPoolExecutor
    private val maintenance = Executors.newSingleThreadExecutor()
    private val visibleDecodes = java.util.concurrent.ThreadPoolExecutor(2, 2, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.LinkedBlockingQueue())
    private val prefetchDecodes = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.LinkedBlockingQueue(),
        java.util.concurrent.ThreadFactory { work ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                work.run()
            }, "artwork-prefetch")
        })
    private val failedFrames = com.miowser.app.performance.WeightedCache<String, Long>(256, 128 * 1024) { key, _ -> key.length * 2 + 48 }
    // A small shared pool prevents a fast grid fling from flooding the general IO pool.
    val artworkDispatcher = executor.asCoroutineDispatcher()
    private val decodeLocks = Array(16) { Any() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturesInFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val downloadThumbnailLocks = Array(16) { Any() }

    fun peek(thumbnailUri: String?): Bitmap? = thumbnailUri?.let(memoryCache::get)

    fun isMiddleFrame(thumbnailUri: String?): Boolean =
        isAvailable(thumbnailUri) && thumbnailUri
            ?.substringAfterLast('/')?.startsWith("${THUMBNAIL_CACHE_VERSION}_middle_") == true

    /**
     * Fallback for streams that cannot be independently sought by the platform extractor.
     */
    // PlayerView surface access is an unstable Media3 API; review on Media3 upgrades.
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
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
        val runtime = Runtime.getRuntime()
        if (!thumbnailCaptureHasHeadroom(captureWidth, captureHeight,
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()))) {
            capturesInFlight.remove(captureKey)
            onComplete(null)
            return
        }
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
    ): () -> Unit {
        val captureKey = "$identityKey:middle"
        if (!capturesInFlight.add(captureKey)) {
            onComplete(null)
            return {}
        }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val phase = java.util.concurrent.atomic.AtomicInteger(0) // queued/running/cancelled
        val task = java.util.concurrent.FutureTask<Unit> {
            if (phase.compareAndSet(0, 1)) {
                val frame = extractFrame(context.applicationContext, mediaUri, headers, durationMs.takeIf { it > 0L })
                if (cancelled.get() || frame == null) {
                    frame?.recycle()
                    capturesInFlight.remove(captureKey)
                    if (!cancelled.get()) mainHandler.post { if (!cancelled.get()) onComplete(null) }
                } else {
                    storeAsync(context.applicationContext, identityKey, captureKey, frame, true, null) {
                        if (!cancelled.get()) onComplete(it)
                    }
                }
            }
        }
        executor.execute(task)
        return {
            cancelled.set(true)
            if (phase.compareAndSet(0, 2)) {
                task.cancel(false)
                executor.remove(task)
                capturesInFlight.remove(captureKey)
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
        com.miowser.app.persistence.RestoreWriteFence.write { synchronized(downloadThumbnailLocks[(mediaUri.hashCode() and Int.MAX_VALUE) % downloadThumbnailLocks.size]) {
            val source = mediaUri.toString()
            LocalInspectionCache.peek("download-preview", source)?.let { return@synchronized it.ifBlank { null } }
            val ticket = LocalInspectionCache.ticket()
            existingDownloadedThumbnail(context, mediaUri)?.let {
                LocalInspectionCache.remember("download-preview", source, it, ticket = ticket)
                return@synchronized it
            }
            val frame = extractFrame(context, mediaUri, null, null) ?: run {
                LocalInspectionCache.remember("download-preview", source, "", LocalInspectionCache.RETRY_MS, ticket)
                return@synchronized null
            }
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
                    Uri.fromFile(target).toString().also {
                        LocalInspectionCache.remember("download-preview", source, it, ticket = ticket)
                    }
                }.getOrNull()
            } finally {
                if (thumbnail !== frame) thumbnail.recycle()
                frame.recycle()
            }
        } }

    /** Remove queued work when a card leaves the viewport; extraction has a separate pool. */
    suspend fun loadVisible(context: Context, thumbnailUri: String): Bitmap? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val task = java.util.concurrent.FutureTask<Unit> {
                if (continuation.isActive) {
                    val bitmap = load(context.applicationContext, thumbnailUri)
                    if (continuation.isActive) continuation.resumeWith(Result.success(bitmap))
                }
            }
            continuation.invokeOnCancellation { task.cancel(false); visibleDecodes.remove(task) }
            visibleDecodes.execute(task)
        }

    /** Warm saved thumbnails just beyond the grid; never delay a visible card's decode. */
    suspend fun prefetch(context: Context, thumbnailUri: String) {
        if (peek(thumbnailUri) != null) return
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            val task = java.util.concurrent.FutureTask<Unit> {
                if (continuation.isActive) load(context.applicationContext, thumbnailUri)
                if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
            }
            continuation.invokeOnCancellation { task.cancel(false); prefetchDecodes.remove(task) }
            prefetchDecodes.execute(task)
        }
    }

    fun removeDownloadedThumbnail(context: Context, mediaUri: Uri?) {
        if (mediaUri == null) return
        maintenance.execute {
            com.miowser.app.persistence.RestoreWriteFence.write {
            val file = File(
                File(context.applicationContext.filesDir, DOWNLOAD_THUMBNAIL_DIRECTORY),
                thumbnailCacheFileName("DOWNLOAD:$mediaUri", middle = true)
            )
            memoryCache.remove(Uri.fromFile(file).toString())
            runCatching { file.delete() }
                    }
        }
    }

    fun pruneDownloadedAsync(context: Context, retainedUris: Collection<Uri>) {
        val pruneStartedAt = System.currentTimeMillis()
        val retainedNames = retainedUris.map {
            thumbnailCacheFileName("DOWNLOAD:$it", middle = true)
        }.toSet()
        maintenance.execute {
            com.miowser.app.persistence.RestoreWriteFence.write {
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
    }

    private fun extractFrame(
        context: Context,
        mediaUri: Uri,
        headers: Map<String, String>?,
        durationMs: Long?
    ): Bitmap? {
        val key = mediaUri.toString()
        if ((failedFrames[key] ?: 0) > System.currentTimeMillis()) return null
        return runCatching {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    middleUs, MediaMetadataRetriever.OPTION_CLOSEST, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT
                )
            } else {
                // API 26 can only decode full-size frames. Refuse unknown/oversized inputs
                // before allocating; the existing placeholder/player capture remains available.
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (!legacyThumbnailFrameIsSafe(width, height)) return@runCatching null
                val frame = retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@runCatching null
                try {
                    fitInside16By9(frame)
                } finally {
                    frame.recycle()
                }
            }
        } finally {
            retriever.release()
        }
    }.getOrNull().also { result ->
            if (result == null) failedFrames[key] = System.currentTimeMillis() + 5 * 60_000
            else failedFrames.remove(key)
        }
    }

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
        // Deduplicate overlapping card requests without retaining an unbounded URI lock map.
        synchronized(decodeLocks[(thumbnailUri.hashCode() and Int.MAX_VALUE) % decodeLocks.size]) {
            memoryCache.get(thumbnailUri)?.let { return it }
            val decoded = runCatching {
                val uri = Uri.parse(thumbnailUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = com.miowser.app.performance.boundedImageSample(
                    bounds.outWidth, bounds.outHeight, THUMBNAIL_WIDTH * 2) ?: return null
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull() ?: run { LocalInspectionCache.forgetArtwork(thumbnailUri); return null }
            decoded.prepareToDraw()
            memoryCache.put(thumbnailUri, decoded)
            return decoded
        }
    }

    /** Removes thumbnails whose Library records no longer exist; runs only after app restore. */
    fun pruneAsync(context: Context, retainedThumbnailUris: Collection<String>, stillCurrent: () -> Boolean = { true }) {
        val pruneStartedAt = System.currentTimeMillis()
        val retainedPaths = retainedThumbnailUris.mapNotNull { value ->
            runCatching { Uri.parse(value).path }.getOrNull()
        }.toSet()
        maintenance.execute {
            if (!stillCurrent()) return@execute
            com.miowser.app.persistence.RestoreWriteFence.write {
            File(context.applicationContext.filesDir, THUMBNAIL_DIRECTORY)
                .listFiles()
                ?.filter { it.isFile && it.extension == "jpg" &&
                    it.absolutePath !in retainedPaths && it.lastModified() < pruneStartedAt }
                ?.forEach { file ->
                    if (!stillCurrent()) return@forEach
                    memoryCache.remove(Uri.fromFile(file).toString())
                    runCatching { file.delete() }
                }
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
            val result = com.miowser.app.persistence.RestoreWriteFence.write { runCatching {
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
            }.getOrNull() }
            if (!captured.isRecycled) captured.recycle()
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

/** Capture plus rescale can briefly retain two bitmaps; artwork must leave UI headroom. */
internal fun thumbnailCaptureHasHeadroom(width: Int, height: Int, availableBytes: Long): Boolean =
    width in 1..MAX_CAPTURE_EDGE && height in 1..MAX_CAPTURE_EDGE &&
        availableBytes >= width.toLong() * height * 8 + 8L * 1024 * 1024

internal fun thumbnailCacheFileName(identityKey: String, middle: Boolean = true): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identityKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "${THUMBNAIL_CACHE_VERSION}_${if (middle) "middle" else "fallback"}_$digest.jpg"
}

/** Bound the API-26 full-frame fallback to at most 4 MP (about 16 MiB in ARGB_8888). */
internal fun legacyThumbnailFrameIsSafe(width: Int?, height: Int?): Boolean =
    width != null && height != null && width in 1..4_096 && height in 1..4_096 &&
        width.toLong() * height <= 4_194_304L

private fun boundedCaptureSize(width: Int, height: Int): Pair<Int, Int> {
    if (width <= MAX_CAPTURE_EDGE && height <= MAX_CAPTURE_EDGE) return width to height
    val scale = MAX_CAPTURE_EDGE.toFloat() / maxOf(width, height)
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}
