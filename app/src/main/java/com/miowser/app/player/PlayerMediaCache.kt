package com.miowser.app.player

import android.content.Context
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CACHE_IDLE_MS = 10 * 60_000L
private const val CACHE_MAX_BYTES = 768L * 1024 * 1024
private const val MIN_FREE_FOR_PREFETCH = 96L * 1024 * 1024
private const val PREFS = "player_stream_cache"
private const val CLEANUP_WORK_PREFIX = "miowser-player-cache-"

/** A bounded, app-private playback cache shared across player instances. Never stores credentials in keys. */
@OptIn(UnstableApi::class)
internal object PlayerMediaCache {
    private val maintenance = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Miowser-player-cache").apply { isDaemon = true }
    }
    private val active = mutableMapOf<String, Int>()
    private var cache: SimpleCache? = null
    private var appContext: Context? = null

    @Synchronized
    fun open(context: Context, url: String): PlayerCacheSession {
        val app = context.applicationContext
        val instance = ensureCache(app)
        val videoId = sha256(url)
        WorkManager.getInstance(app).cancelUniqueWork(CLEANUP_WORK_PREFIX + videoId)
        val lastUsed = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(videoId, 0L)
        if (playerCacheExpired(lastUsed, System.currentTimeMillis(), active[videoId] ?: 0)) {
            instance.keys.filter { it.startsWith("$videoId:") }.forEach { key ->
                runCatching { instance.removeResource(key) }
            }
        }
        active[videoId] = (active[videoId] ?: 0) + 1
        markUsed(app, videoId)
        maintenance.execute { pruneExpired() }
        return PlayerCacheSession(videoId, instance, app)
    }

    @Synchronized
    private fun ensureCache(app: Context): SimpleCache = cache ?: run {
            val directory = File(app.cacheDir, "player_stream_cache").apply { mkdirs() }
            val free = runCatching { StatFs(directory.absolutePath).availableBytes }.getOrDefault(CACHE_MAX_BYTES)
            val limit = minOf(CACHE_MAX_BYTES, maxOf(32L * 1024 * 1024, free / 4))
            SimpleCache(directory, LeastRecentlyUsedCacheEvictor(limit), StandaloneDatabaseProvider(app))
                .also { cache = it; appContext = app }
    }

    @Synchronized
    private fun closeOnWorker(session: PlayerCacheSession) {
        val count = active[session.videoId] ?: return
        if (count <= 1) active.remove(session.videoId) else active[session.videoId] = count - 1
        markUsed(session.context, session.videoId)
        maintenance.schedule({ pruneExpired() }, CACHE_IDLE_MS, TimeUnit.MILLISECONDS)
        val work = OneTimeWorkRequestBuilder<PlayerCacheCleanupWorker>()
            .setInitialDelay(CACHE_IDLE_MS, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(session.context).enqueueUniqueWork(
            CLEANUP_WORK_PREFIX + session.videoId, ExistingWorkPolicy.REPLACE, work)
    }

    fun close(session: PlayerCacheSession) { maintenance.execute { closeOnWorker(session) } }

    @Synchronized
    fun cleanup(context: Context) {
        ensureCache(context.applicationContext)
        pruneExpired()
    }

    fun hasPrefetchSpace(context: Context): Boolean =
        runCatching { StatFs(context.cacheDir.absolutePath).availableBytes > MIN_FREE_FOR_PREFETCH }
            .getOrDefault(false)

    @Synchronized
    private fun pruneExpired() {
        val app = appContext ?: return
        val instance = cache ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val expired = prefs.all.mapNotNull { (id, value) ->
            val lastUsed = value as? Long ?: return@mapNotNull null
            id.takeIf { it !in active && playerCacheExpired(lastUsed, now) }
        }
        expired.forEach { id ->
            instance.keys.filter { it.startsWith("$id:") }.forEach { key ->
                runCatching { instance.removeResource(key) }
            }
        }
        if (expired.isNotEmpty()) prefs.edit().apply {
            expired.forEach(::remove)
            apply()
        }
        // A previous process may have died before recording a key. Do not retain orphaned bytes.
        instance.keys.filter { key ->
            val id = key.substringBefore(':')
            id !in active && !prefs.contains(id)
        }.forEach { key -> runCatching { instance.removeResource(key) } }
    }

    private fun markUsed(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(id, System.currentTimeMillis()).apply()
    }
}

internal class PlayerCacheCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        PlayerMediaCache.cleanup(applicationContext)
        Result.success()
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

@OptIn(UnstableApi::class)
internal class PlayerCacheSession internal constructor(
    val videoId: String,
    private val cache: SimpleCache,
    val context: Context
) {
    private val keyFactory = CacheKeyFactory { spec ->
        playerCacheKey(videoId, spec.uri.toString(), spec.httpRequestHeaders)
    }
    @Volatile private var currentWriter: CacheWriter? = null
    @Volatile private var currentSource: DataSource? = null
    private val cleanup = Executors.newSingleThreadExecutor { Thread(it, "Miowser-cache-request-close") }
    @Volatile private var closed = false

    fun factory(upstream: DataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheKeyFactory(keyFactory)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun cancelPrefetch() {
        currentWriter?.cancel()
        currentSource?.let { source -> runCatching { cleanup.execute { runCatching { source.close() } } } }
    }

    private fun key(spec: DataSpec) = keyFactory.buildCacheKey(spec)
    private fun resourceLength(spec: DataSpec): Long = if (spec.length >= 0) spec.length else {
        val end = androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(key(spec)))
        if (end < 0) -1 else (end - spec.position).coerceAtLeast(0)
    }
    fun isCached(spec: DataSpec): Boolean {
        val length = resourceLength(spec)
        return length >= 0 && cache.isCached(key(spec), spec.position, length)
    }

    /** Fill the first missing span. A cap is a resumable piece, never evidence of completion. */
    fun prefetch(factory: CacheDataSource.Factory, spec: DataSpec, checkActive: () -> Unit): Boolean {
        if (closed || !PlayerMediaCache.hasPrefetchSpace(context) || spec.length == 0L) return false
        val length = resourceLength(spec)
        if (length == 0L || isCached(spec)) return false
        val limit = 12L * 1024 * 1024
        val missing = firstMissingCachePiece(spec.position, length, limit) { position, amount ->
            cache.getCachedLength(key(spec), position, amount)
        } ?: return false
        val position = missing.position
        val amount = missing.length.takeIf { it >= 0 } ?: limit
        val piece = spec.buildUpon().setPosition(position).setLength(missing.length).build()
        val before = cache.getCachedBytes(key(spec), position, amount)
        var capped = false
        var fetchedBytes = 0L
        val source = factory.createDataSourceForDownloading()
        currentSource = source
        lateinit var writer: CacheWriter
        writer = CacheWriter(source, piece, null) { _, _, newBytes ->
            fetchedBytes += newBytes.coerceAtLeast(0)
            if (missing.length < 0 && fetchedBytes >= limit) { capped = true; writer.cancel() }
        }
        currentWriter = writer
        try {
            checkActive()
            if (!closed) try { writer.cache() } catch (error: java.io.IOException) { if (!capped) throw error }
            checkActive()
        }
        finally { currentWriter = null; currentSource = null; runCatching { source.close() } }
        return cache.getCachedBytes(key(spec), position, amount) > before
    }

    fun close() {
        if (closed) return
        closed = true
        cancelPrefetch()
        PlayerMediaCache.close(this)
        cleanup.shutdown()
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Position-independent key; request credentials influence the hash but never appear in cache metadata. */
internal fun playerCacheKey(videoId: String, uri: String, headers: Map<String, String>): String {
    val identityHeaders = headers.entries.asSequence()
        .filterNot { it.key.equals("Range", true) }
        .sortedBy { it.key.lowercase() }
        .joinToString("|") { "${it.key.lowercase()}=${it.value}" }
    return "$videoId:${sha256(uri + "|" + identityHeaders)}"
}

internal fun playerCacheExpired(lastUsedAt: Long, now: Long, activeSessions: Int = 0): Boolean =
    activeSessions == 0 && lastUsedAt > 0L && now >= lastUsedAt && now - lastUsedAt >= CACHE_IDLE_MS
