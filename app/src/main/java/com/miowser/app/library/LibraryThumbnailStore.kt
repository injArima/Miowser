package com.miowser.app.library

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.miowser.app.downloader.ApkIconLoader
import com.miowser.app.library.device.DeviceVideo
import com.miowser.app.library.device.canonicalVideoUri
import com.miowser.app.library.device.localArtworkRevision
import com.miowser.app.library.device.mediaArtworkRevision
import com.miowser.app.performance.traceSection
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val MAX_POSTER_BYTES = 4L * 1024 * 1024
internal const val LOCAL_ARTWORK_DIRECTORY = "local_video_artwork"

internal enum class LibraryThumbnailKind(val fileLabel: String, val priority: Int) {
    FALLBACK("fallback", 1),
    POSTER("poster", 2),
    MIDDLE("middle", 3)
}

/** Captures and owns persistent artwork generated from Media3's already-decoded video frame. */
object LibraryThumbnailStore {
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(12)) { work ->
        artworkThread(work, "artwork-capture")
    }
    private val maintenance = Executors.newSingleThreadExecutor()
    private val queue = ArtworkQueue<Bitmap>(Executors.newFixedThreadPool(2) { artworkThread(it, "artwork-preview") })
    private val previewWrites = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4)) {
        artworkThread(it, "artwork-disk")
    }
    private val failedFrames = com.miowser.app.performance.WeightedCache<String, Long>(256, 128 * 1024) { key, _ -> key.length * 2 + 48 }
    private val failedDecodes = com.miowser.app.performance.WeightedCache<String, Long>(256, 128 * 1024) { key, _ -> key.length * 2 + 48 }
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturesInFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private val memoryCache = object : LruCache<String, Bitmap>(
        min(MEMORY_CACHE_KIB, (Runtime.getRuntime().maxMemory() / (12 * 1024)).toInt())
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            ((value.allocationByteCount + 1023) / 1024).coerceAtLeast(1)
    }
    private var localDisk: LocalArtworkDiskCache? = null

    private fun artworkThread(work: Runnable, name: String) = Thread({
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        work.run()
    }, name)

    fun peek(thumbnailUri: String?): Bitmap? = thumbnailUri?.let(memoryCache::get)

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                previewWrites.queue.clear()
                memoryCache.evictAll()
            }
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> memoryCache.trimToSize(memoryCache.maxSize() / 2)
        }
    }

    @Synchronized
    private fun disk(context: Context): LocalArtworkDiskCache =
        localDisk ?: LocalArtworkDiskCache(localArtworkDirectory(context)).also { localDisk = it }

    private fun catalogRevision(video: DeviceVideo): String =
        if (video.sourceId.startsWith("media:") && Build.VERSION.SDK_INT >= 30 && !video.revision.startsWith("media:"))
            "media:${video.revision}"
        else video.revision

    internal fun peekDeviceFrame(video: DeviceVideo, compact: Boolean): Bitmap? {
        // Linked aliases may need MediaStore metadata before their cache key is known.
        if (video.canonicalUri != video.uri && !video.sourceId.startsWith("media:")) return null
        val revision = catalogRevision(video).takeIf(::hasReliableArtworkRevision) ?: return null
        return peek(localArtworkKey(video.canonicalUri, revision, compact))
    }

    internal suspend fun loadDeviceFrame(
        context: Context, video: DeviceVideo, visible: Boolean = true, compact: Boolean = false,
        cacheOnly: Boolean = false
    ): Bitmap? {
        val uri = Uri.parse(video.uri)
        val canonical = Uri.parse(video.canonicalUri)
        val revision = if (video.sourceId.startsWith("media:")) {
            catalogRevision(video)
        } else if (canonical.authority == "media") {
            withContext(Dispatchers.IO) { mediaArtworkRevision(context, canonical) } ?: video.revision
        } else video.revision
        return loadLocalFrame(context, uri, canonical, revision, video.durationMs, visible, compact,
            midpoint = false, cacheOnly = cacheOnly)
    }

    internal suspend fun loadLocalFrame(
        context: Context, uri: Uri, durationMs: Long = 0, midpoint: Boolean = false
    ): Bitmap? {
        val (canonical, revision) = withContext(Dispatchers.IO) {
            val canonical = Uri.parse(canonicalVideoUri(context, uri))
            canonical to localArtworkRevision(context, uri, canonical)
        }
        return loadLocalFrame(context, uri, canonical, revision, durationMs, visible = true, compact = false, midpoint = midpoint)
    }

    private suspend fun loadLocalFrame(
        context: Context, uri: Uri, canonical: Uri, revision: String?, durationMs: Long, visible: Boolean,
        compact: Boolean, midpoint: Boolean, cacheOnly: Boolean = false
    ): Bitmap? {
        val knownRevision = revision?.takeIf(::hasReliableArtworkRevision)
        val key = localArtworkKey(if (knownRevision == null) uri.toString() else canonical.toString(),
            knownRevision ?: "unversioned", compact, midpoint)
        if (knownRevision != null) peek(key)?.let { return it }
        val width = if (compact) THUMBNAIL_WIDTH / 2 else THUMBNAIL_WIDTH
        val height = if (compact) THUMBNAIL_HEIGHT / 2 else THUMBNAIL_HEIGHT
        val name = thumbnailCacheFileName(key)
        if (knownRevision != null) {
            queue.load("$key:disk", visible) {
                peek(key) ?: runCatching {
                    val store = disk(context)
                    val bitmap = traceSection("LocalArtworkDiskDecode") {
                        store.read(name) { BitmapFactory.decodeFile(it.absolutePath) }
                            ?: if (compact) {
                                val larger = localArtworkKey(canonical.toString(), knownRevision, midpoint = midpoint)
                                store.read(thumbnailCacheFileName(larger)) {
                                    BitmapFactory.decodeFile(it.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
                                }
                            } else null
                    }
                    bitmap?.also {
                        it.prepareToDraw()
                        memoryCache.put(key, it)
                    }
                }.getOrNull()
            }?.let { return it }
        }
        if (cacheOnly) return null
        return queue.load(key, visible, frame = true) {
            if (knownRevision != null) peek(key)?.let { return@load it }
            val frame = traceSection("LocalArtworkFrameDecode") {
                extractFrame(context, uri, durationMs.takeIf { it > 0 }, key, width, height, midpoint)
            } ?: return@load null
            frame.prepareToDraw()
            if (knownRevision != null) {
                memoryCache.put(key, frame)
                try {
                    previewWrites.execute {
                        runCatching { disk(context).write(name) { frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }
                    }
                } catch (_: RejectedExecutionException) {
                    // Keep the decoded frame available even when the background disk queue is full.
                }
            }
            frame
        }
    }

    fun isMiddleFrame(thumbnailUri: String?): Boolean =
        thumbnailKind(thumbnailUri) == LibraryThumbnailKind.MIDDLE

    /** Prevents asynchronous fallback/poster jobs from replacing a better frame that won a race. */
    fun shouldReplace(currentUri: String?, candidateUri: String): Boolean {
        val candidate = thumbnailKind(candidateUri) ?: return false
        val current = thumbnailKind(currentUri) ?: return true
        return candidate.priority > current.priority
    }

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
        if (!thumbnailCaptureHasHeadroom(captureWidth, captureHeight, availableArtworkMemory())) {
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
                    storeAsync(context.applicationContext, identityKey, captureKey, bitmap,
                        if (middle) LibraryThumbnailKind.MIDDLE else LibraryThumbnailKind.FALLBACK,
                        sourceAspect, onComplete = onComplete)
                }
            }

            is SurfaceView -> {
                val bitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
                PixelCopy.request(surface, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        storeAsync(context.applicationContext, identityKey, captureKey, bitmap,
                            if (middle) LibraryThumbnailKind.MIDDLE else LibraryThumbnailKind.FALLBACK,
                            sourceAspect, onComplete = onComplete)
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

    /**
     * Copies a page-provided poster into Miowser's bounded persistent artwork cache. The response
     * is streamed to a temporary file so a large or dishonest image cannot occupy the app heap.
     */
    fun cachePoster(
        context: Context,
        posterUrl: String,
        headers: Map<String, String>,
        identityKey: String,
        onComplete: (String?) -> Unit
    ): () -> Unit {
        val directory = File(context.filesDir, THUMBNAIL_DIRECTORY)
        val target = File(directory, thumbnailCacheFileName(identityKey, LibraryThumbnailKind.POSTER))
        target.takeIf { it.isFile && it.length() > 0L }?.let {
            onComplete(Uri.fromFile(it).toString())
            return {}
        }
        val captureKey = "$identityKey:poster"
        if (!capturesInFlight.add(captureKey)) {
            onComplete(null)
            return {}
        }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val task = java.util.concurrent.FutureTask<Unit> {
            val bitmap = downloadPoster(context.applicationContext, posterUrl, headers, cancelled)
            if (bitmap == null || cancelled.get()) {
                bitmap?.recycle()
                capturesInFlight.remove(captureKey)
                if (!cancelled.get()) mainHandler.post { onComplete(null) }
            } else {
                storeAsync(context.applicationContext, identityKey, captureKey, bitmap,
                    LibraryThumbnailKind.POSTER, null) { uri ->
                    if (!cancelled.get()) onComplete(uri)
                }
            }
        }
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            capturesInFlight.remove(captureKey)
            onComplete(null)
            return {}
        }
        return {
            cancelled.set(true)
            if (task.cancel(false)) {
                executor.remove(task)
                capturesInFlight.remove(captureKey)
            }
        }
    }

    suspend fun loadVisible(context: Context, thumbnailUri: String): Bitmap? =
        loadArtwork(context, thumbnailUri, visible = true)

    suspend fun prefetch(context: Context, thumbnailUri: String) {
        loadArtwork(context, thumbnailUri, visible = false)
    }

    private suspend fun loadArtwork(context: Context, thumbnailUri: String, visible: Boolean): Bitmap? =
        peek(thumbnailUri) ?: queue.load(thumbnailUri, visible) { load(context, thumbnailUri) }

    internal suspend fun loadApkIcon(context: Context, uri: Uri): Bitmap? =
        queue.load("apk:$uri", visible = true) { ApkIconLoader.load(context, uri) }

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
        durationMs: Long?,
        key: String,
        width: Int,
        height: Int,
        midpoint: Boolean
    ): Bitmap? {
        if ((failedFrames[key] ?: 0) > System.currentTimeMillis()) return null
        if (!thumbnailCaptureHasHeadroom(width, height, availableArtworkMemory())) return null
        if (!midpoint && Build.VERSION.SDK_INT >= 29) {
            val size = Size(width, height)
            runCatching {
                when (mediaUri.scheme) {
                    "content" -> context.contentResolver.loadThumbnail(mediaUri, size, null)
                    "file" -> mediaUri.path?.let { ThumbnailUtils.createVideoThumbnail(File(it), size, null) }
                    else -> null
                }
            }.getOrNull()?.let { return boundedPreview(it, width, height) }
        }
        var lowMemory = false
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaUri)
                val duration = durationMs ?: retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val middleUs = duration?.takeIf { it > 0L }?.let {
                    (if (midpoint) it / 2L else it / 5L * 2L) * 1_000L
                } ?: 0L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    return@runCatching retriever.getScaledFrameAtTime(
                        middleUs, MediaMetadataRetriever.OPTION_CLOSEST, width, height
                    )
                }
                // API 26 decodes at source size, so check both dimensions and current heap headroom.
                val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (!legacyThumbnailFrameIsSafe(sourceWidth, sourceHeight)) return@runCatching null
                val bytes = checkNotNull(sourceWidth).toLong() * checkNotNull(sourceHeight) * 4L
                if (availableArtworkMemory() < bytes + 9L * 1024 * 1024) {
                    lowMemory = true
                    return@runCatching null
                }
                val frame = retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@runCatching null
                boundedPreview(frame, width, height)
            } finally {
                retriever.release()
            }
        }.getOrNull().also { result ->
            if (result != null) failedFrames.remove(key)
            else if (!lowMemory) failedFrames[key] = System.currentTimeMillis() + 5 * 60_000
        }
    }

    private fun boundedPreview(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        if (scale >= 1f) return bitmap
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun downloadPoster(
        context: Context,
        posterUrl: String,
        headers: Map<String, String>,
        cancelled: java.util.concurrent.atomic.AtomicBoolean
    ): Bitmap? {
        val uri = runCatching { Uri.parse(posterUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val temporary = runCatching { File.createTempFile("library-poster-", ".part", context.cacheDir) }
            .getOrNull() ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(posterUrl).openConnection() as? HttpURLConnection)?.apply {
                connectTimeout = 6_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value)
                }
            } ?: return null
            val response = connection.responseCode
            if (response !in 200..299 || connection.contentLengthLong > MAX_POSTER_BYTES) return null
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (!cancelled.get()) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_POSTER_BYTES) return null
                        output.write(buffer, 0, read)
                    }
                    if (cancelled.get() || total == 0L) return null
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width > 32_768 || height > 32_768 ||
                width.toLong() * height > 128L * 1024 * 1024) return null
            var sample = 1
            while (width / sample > MAX_CAPTURE_EDGE || height / sample > MAX_CAPTURE_EDGE) sample *= 2
            BitmapFactory.decodeFile(temporary.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
            temporary.delete()
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

    private fun load(context: Context, thumbnailUri: String): Bitmap? {
        peek(thumbnailUri)?.let { return it }
        if ((failedDecodes[thumbnailUri] ?: 0) > System.currentTimeMillis()) return null
        val decoded = runCatching {
            traceSection("LibraryArtworkDecode") {
                val uri = Uri.parse(thumbnailUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = com.miowser.app.performance.boundedImageSample(
                    bounds.outWidth, bounds.outHeight, THUMBNAIL_WIDTH) ?: return@traceSection null
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull() ?: run {
            failedDecodes[thumbnailUri] = System.currentTimeMillis() + 30_000
            LocalInspectionCache.forgetArtwork(thumbnailUri)
            return null
        }
        decoded.prepareToDraw()
        memoryCache.put(thumbnailUri, decoded)
        return decoded
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
        kind: LibraryThumbnailKind,
        sourceAspectRatio: Float?,
        recycleCapture: Boolean = true,
        onComplete: (String?) -> Unit
    ) {
        val work = Runnable {
            val result = com.miowser.app.persistence.RestoreWriteFence.write { runCatching {
                check(bitmapLooksUsable(captured)) { "Captured frame is blank" }
                val thumbnail = fitInside16By9(captured, sourceAspectRatio)
                val directory = File(context.filesDir, THUMBNAIL_DIRECTORY).apply { mkdirs() }
                check(directory.isDirectory) { "Could not create thumbnail directory" }
                val target = File(directory, thumbnailCacheFileName(identityKey, kind))
                val temporary = File(directory, "${target.name}.tmp")
                val compressed = try {
                    temporary.outputStream().buffered().use { output ->
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                    }
                } finally {
                    if (thumbnail !== captured) thumbnail.recycle()
                }
                check(compressed) { "Could not encode thumbnail" }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                Uri.fromFile(target).toString().also {
                    memoryCache.remove(it)
                    failedDecodes.remove(it)
                }
            }.getOrNull() }
            if (recycleCapture) captured.recycle()
            capturesInFlight.remove(captureKey)
            mainHandler.post { onComplete(result) }
        }
        try {
            executor.execute(work)
        } catch (_: RejectedExecutionException) {
            if (recycleCapture) captured.recycle()
            capturesInFlight.remove(captureKey)
            mainHandler.post { onComplete(null) }
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

internal fun hasReliableArtworkRevision(revision: String): Boolean =
    revision.startsWith("media:") || (revision.substringBefore(':').toLongOrNull() ?: 0L) > 0L

internal fun localArtworkKey(identity: String, revision: String, compact: Boolean = false, midpoint: Boolean = false): String {
    val size = if (compact) "320x180" else "640x360"
    val kind = if (midpoint) "midpoint" else "local"
    return "$kind:$size:${identity.length}:$identity:$revision"
}

internal fun localArtworkDirectory(context: Context): File {
    val persistent = File(context.filesDir, LOCAL_ARTWORK_DIRECTORY)
    val legacy = File(context.cacheDir, LOCAL_ARTWORK_DIRECTORY)
    if (persistent.isDirectory || !legacy.isDirectory || legacy.renameTo(persistent)) return persistent
    return legacy
}

private fun availableArtworkMemory(): Long = Runtime.getRuntime().run {
    maxMemory() - (totalMemory() - freeMemory())
}

/** Capture plus rescale can briefly retain two bitmaps; artwork must leave UI headroom. */
internal fun thumbnailCaptureHasHeadroom(width: Int, height: Int, availableBytes: Long): Boolean =
    width in 1..MAX_CAPTURE_EDGE && height in 1..MAX_CAPTURE_EDGE &&
        availableBytes >= width.toLong() * height * 8 + 8L * 1024 * 1024

internal fun thumbnailCacheFileName(identityKey: String, middle: Boolean = true): String {
    return thumbnailCacheFileName(identityKey,
        if (middle) LibraryThumbnailKind.MIDDLE else LibraryThumbnailKind.FALLBACK)
}

internal fun thumbnailCacheFileName(identityKey: String, kind: LibraryThumbnailKind): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identityKey.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "${THUMBNAIL_CACHE_VERSION}_${kind.fileLabel}_$digest.jpg"
}

private fun thumbnailKind(thumbnailUri: String?): LibraryThumbnailKind? {
    if (!LibraryThumbnailStore.isAvailable(thumbnailUri)) return null
    val name = thumbnailUri?.substringAfterLast('/') ?: return null
    return thumbnailKindFromFileName(name)
}

internal fun thumbnailKindFromFileName(name: String): LibraryThumbnailKind? =
    LibraryThumbnailKind.entries.firstOrNull {
        name.startsWith("${THUMBNAIL_CACHE_VERSION}_${it.fileLabel}_")
    }

private fun bitmapLooksUsable(bitmap: Bitmap): Boolean {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
    val columns = 12
    val rows = 8
    val samples = IntArray(columns * rows)
    var index = 0
    repeat(rows) { row ->
        val y = ((row + 0.5f) * bitmap.height / rows).toInt().coerceIn(0, bitmap.height - 1)
        repeat(columns) { column ->
            val x = ((column + 0.5f) * bitmap.width / columns).toInt().coerceIn(0, bitmap.width - 1)
            samples[index++] = bitmap.getPixel(x, y)
        }
    }
    return thumbnailSamplesLookUsable(samples)
}

/** Rejects secure-surface/transition captures that are effectively transparent or solid black. */
internal fun thumbnailSamplesLookUsable(samples: IntArray): Boolean {
    if (samples.isEmpty()) return false
    var visible = 0
    var nonBlack = 0
    samples.forEach { color ->
        val alpha = color ushr 24 and 0xFF
        if (alpha >= 32) {
            visible++
            val red = color ushr 16 and 0xFF
            val green = color ushr 8 and 0xFF
            val blue = color and 0xFF
            if (maxOf(red, green, blue) >= 18) nonBlack++
        }
    }
    return visible >= samples.size * 3 / 4 && nonBlack >= maxOf(4, samples.size / 12)
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
