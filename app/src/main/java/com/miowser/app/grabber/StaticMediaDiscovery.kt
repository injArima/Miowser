package com.miowser.app.grabber

import android.util.Log
import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Miowser/StaticScan"
private const val MAX_HTML_BYTES = 512 * 1024

data class DiscoveryProgress(val pagesScanned: Int, val pageLimit: Int, val complete: Boolean)

class DiscoveryHandle internal constructor(internal val stopped: AtomicBoolean) {
    internal var future: java.util.concurrent.FutureTask<Unit>? = null
    private var connection: HttpURLConnection? = null
    @Synchronized internal fun track(value: HttpURLConnection) {
        if (stopped.get()) { value.disconnect(); throw java.util.concurrent.CancellationException() }
        connection = value
    }
    @Synchronized internal fun untrack(value: HttpURLConnection) { if (connection === value) connection = null }
    fun cancel() {
        stopped.set(true)
        future?.let { it.cancel(true); StaticMediaDiscovery.remove(it) }
        val retired = synchronized(this) { connection.also { connection = null } }
        retired?.let { ProbeScheduler.disconnect(listOf(it)) }
    }
}

/** A small, explicitly bounded HTML crawl. URLs are only reported as candidates for MediaProbe. */
object StaticMediaDiscovery {
    private val executor = java.util.concurrent.ThreadPoolExecutor(2, 2, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(16))
    internal fun remove(task: Runnable) { executor.remove(task) }

    fun scan(
        startUrl: String,
        userAgent: String,
        depthLimit: Int,
        pageLimit: Int,
        onCandidate: (url: String, type: MediaType, sourcePageUrl: String) -> Unit,
        onProgress: (DiscoveryProgress) -> Unit
    ): DiscoveryHandle {
        val stopped = AtomicBoolean(false)
        val handle = DiscoveryHandle(stopped)
        val task = java.util.concurrent.FutureTask<Unit> {
            if (stopped.get()) return@FutureTask
            val deadline = System.nanoTime() + 45_000_000_000L
            val origin = runCatching { URI(startUrl) }.getOrNull()
            if (origin == null || origin.scheme !in listOf("http", "https") || origin.host == null) {
                onProgress(DiscoveryProgress(0, pageLimit, true))
                return@FutureTask
            }
            val queue = ArrayDeque<Triple<String, Int, String>>()
            val seenPages = mutableSetOf(startUrl)
            val seenMedia = mutableSetOf<String>()
            queue.add(Triple(startUrl, 0, startUrl))
            var scanned = 0
            val cap = pageLimit.coerceIn(1, 16)
            val depth = depthLimit.coerceIn(0, 2)
            while (queue.isNotEmpty() && scanned < cap && !stopped.get() && System.nanoTime() < deadline) {
                val (url, level, referer) = queue.removeFirst()
                if (!DiscoveryScope.allows(origin, URI(url))) continue
                val html = fetchHtml(url, referer, userAgent, origin, handle, deadline)
                scanned++
                if (html != null && !stopped.get()) {
                    val links = StaticMediaHtmlParser.parse(html, url)
                    for (media in links.mediaUrls) {
                        if (stopped.get() || seenMedia.size >= 64) break
                        if (media.length <= 8192 && seenMedia.add(media)) {
                            onCandidate(media, links.mediaTypes.getValue(media), url)
                            if (!stopped.get()) Thread.sleep(100)
                        }
                    }
                    if (level < depth) {
                        for (page in links.pageUrls) {
                            if (seenPages.size >= 128) break
                            if (page.length <= 8192 && DiscoveryScope.allows(origin, URI(page)) && seenPages.add(page)) {
                                queue.add(Triple(page, level + 1, url))
                            }
                        }
                    }
                }
                onProgress(DiscoveryProgress(scanned, cap, false))
                // Rate limit page requests even when a server responds immediately.
                if (queue.isNotEmpty() && !stopped.get()) Thread.sleep(300)
            }
            onProgress(DiscoveryProgress(scanned, cap, true))
        }
        handle.future = task
        try { executor.execute(task) } catch (_: java.util.concurrent.RejectedExecutionException) {
            stopped.set(true)
            onProgress(DiscoveryProgress(0, pageLimit, true))
        }
        return handle
    }

    private fun fetchHtml(
        address: String,
        referer: String,
        userAgent: String,
        origin: URI,
        handle: DiscoveryHandle,
        deadline: Long
    ): String? {
        val stopped = handle.stopped
        var current = address
        repeat(4) {
            if (stopped.get() || System.nanoTime() >= deadline) return null
            val connection = com.miowser.app.network.NativeConnections.open(URL(current),
                com.miowser.app.network.ConnectionBudget.Priority.DISCOVERY) {
                    if (stopped.get()) throw java.util.concurrent.CancellationException()
                }.apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Referer", referer)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                CookieManager.getInstance().getCookie(current)?.let { setRequestProperty("Cookie", it) }
            }
            try {
                handle.track(connection)
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    val next = URI(current).resolve(location)
                    if (!DiscoveryScope.allows(origin, next)) return null
                    current = next.toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val contentType = connection.contentType?.lowercase().orEmpty()
                if (contentType.isNotEmpty() && !contentType.contains("text/html") &&
                    !contentType.contains("application/xhtml+xml")) return null
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() < MAX_HTML_BYTES && !stopped.get() && System.nanoTime() < deadline) {
                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_HTML_BYTES - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                if (stopped.get()) return null
                return bytes.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                com.miowser.app.diagnostics.SafeLog.debug(TAG, "HTML scan skipped $current: ${e.message}")
                return null
            } finally {
                handle.untrack(connection)
                com.miowser.app.network.NativeConnections.close(connection)
            }
        }
        return null
    }
}
