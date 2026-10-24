package com.miowser.app.library

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process mirror of disposable cacheDir data; preload before cards compose. */
internal object LocalInspectionCache {
    const val SUCCESS_MS = 24 * 60 * 60_000L
    const val RETRY_MS = 5 * 60_000L
    private val initialization = Any()
    private val scheduling = Any()
    private val writer = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private var pending: ScheduledFuture<*>? = null
    @Volatile private var cache: InspectionCache? = null
    val epoch: Long get() = cache?.epoch ?: 0L
    private val locks = Array(32) { Any() }
    // Play/Open validation must not wait behind a cold APK icon parse/copy.
    private val availabilityLocks = Array(32) { Any() }
    private val kinds = listOf("available", "type", "metadata", "download-preview", "library-preview", "apk-icon")
    private fun newDigestCache() = com.miowser.app.performance.WeightedCache<String, String>(512, 384 * 1024) { key, value ->
        (key.length + value.length) * 2 + 64
    }
    @Volatile private var digests = newDigestCache()

    fun prepare(context: Context): Long = synchronized(initialization) {
        val store = cache ?: InspectionCache(File(context.applicationContext.cacheDir, "media_inspections"))
            .also { cache = it }
        val old = store.epoch
        runCatching { store.prepare() }.getOrDefault(old)
            .also { if (it != old) {
                digests = newDigestCache()
                com.miowser.app.downloader.ApkIconLoader.clearMemory()
            } }
    }

    fun key(kind: String, uri: String): String {
        val digest = digests[uri] ?: buildString(64) {
            val hex = "0123456789abcdef"
            MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8)).forEach {
                val value = it.toInt() and 0xff
                append(hex[value ushr 4]); append(hex[value and 15])
            }
        }.also { digests[uri] = it }
        return "$kind:$digest"
    }
    fun peek(kind: String, uri: String): String? = cache?.peek(key(kind, uri))
    fun ticket(): Long = cache?.ticket() ?: -1L
    fun remember(kind: String, uri: String, value: String, lifetime: Long = SUCCESS_MS, ticket: Long = LocalInspectionCache.ticket()): Boolean {
        val saved = cache?.put(key(kind, uri), value, lifetime, ticket) == true
        if (saved) scheduleWrite()
        return saved
    }
    fun forget(uri: String?, ticket: Long? = null): Long? {
        if (uri == null) return null
        val result = cache?.invalidate(kinds.map { key(it, uri) }, ticket)
        if (result != null) {
            com.miowser.app.downloader.ApkIconLoader.forget(uri)
            scheduleWrite()
        }
        return result
    }
    fun forgetArtwork(value: String) { cache?.invalidateValue(value); scheduleWrite() }
    fun <T> inspect(uri: String, work: () -> T): T =
        synchronized(locks[(uri.hashCode() and Int.MAX_VALUE) % locks.size], work)

    fun available(context: Context, uri: String, force: Boolean = false): Boolean =
        synchronized(availabilityLocks[(uri.hashCode() and Int.MAX_VALUE) % availabilityLocks.size]) probe@ {
        if (!force) peek("available", uri)?.toBooleanStrictOrNull()?.let { return@probe it }
        val ticket = ticket()
        val available = runCatching {
            val target = uri.toUri()
            when (target.scheme?.lowercase()) {
                "file" -> target.path?.let { File(it).isFile && File(it).canRead() } == true
                "content" -> context.contentResolver.openFileDescriptor(target, "r")?.use { true } == true
                else -> false
            }
        }.getOrDefault(false)
        val savedTicket = if (!available) forget(uri, ticket) ?: return@probe available else ticket
        remember("available", uri, available.toString(), if (available) SUCCESS_MS else RETRY_MS,
            savedTicket)
        available
    }

    private fun scheduleWrite() = synchronized(scheduling) {
        pending?.cancel(false)
        pending = writer.schedule({ runCatching { cache?.flush() } }, 250, TimeUnit.MILLISECONDS)
    }
}
