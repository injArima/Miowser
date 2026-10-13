package com.miowser.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.nio.charset.Charset

internal data class SpeedDialSiteMetadata(val title: String?, val icon: Bitmap?)

/** Small, persistent favicon cache shared by saved speed dials. Network work never runs on the UI thread. */
internal object SpeedDialSiteMetadataStore {
    private const val MAX_ICON_BYTES = 512 * 1024
    private const val MAX_PAGE_BYTES = 192 * 1024
    private const val ICON_EDGE = 64
    private const val MAX_CACHED_ICONS = 512
    private const val MAX_CACHE_BYTES = 16L * 1024 * 1024
    private const val ICON_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private val networkSlots = Semaphore(3)
    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount + key.length * 2
    }
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cacheVisitedIcon(context: Context, pageUrl: String, bitmap: Bitmap) {
        val visitedOrigin = origin(pageUrl) ?: return
        if (SpeedDials.entries.none { origin(it.url) == visitedOrigin }) return
        cacheScope.launch { saveVisitedIcon(context.applicationContext, pageUrl, bitmap) }
    }

    suspend fun cachedIcon(context: Context, pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val origin = origin(pageUrl) ?: return@withContext null
        synchronized(memory) { memory.get(origin) }?.let { return@withContext it }
        val file = iconFile(context, origin)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = com.miowser.app.performance.boundedImageSample(bounds.outWidth, bounds.outHeight, ICON_EDGE * 2)
            ?: return@withContext null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        synchronized(memory) { memory.put(origin, bitmap) }
        bitmap
    }

    suspend fun fetch(context: Context, pageUrl: String, needTitle: Boolean): SpeedDialSiteMetadata =
        withContext(Dispatchers.IO) {
            val origin = origin(pageUrl) ?: return@withContext SpeedDialSiteMetadata(null, null)
            val cached = cachedIcon(context, pageUrl)
            val iconFresh = cached != null && System.currentTimeMillis() - iconFile(context, origin).lastModified() < ICON_MAX_AGE_MS
            if (iconFresh && !needTitle) return@withContext SpeedDialSiteMetadata(null, cached)
            networkSlots.withPermit {
                var title: String? = null
                var icon = cached
                val page = readHttp(pageUrl, MAX_PAGE_BYTES, htmlOnly = true)
                if (page != null) {
                    val html = page.bytes.toString(page.charset)
                    title = pageTitle(html, pageUrl)
                }
                if (!iconFresh) {
                    val candidates = iconCandidates(page?.finalUrl ?: pageUrl, page?.let { it.bytes.toString(it.charset) })
                    for (url in candidates.take(5)) {
                        val response = readHttp(url, MAX_ICON_BYTES, htmlOnly = false) ?: continue
                        val decoded = decodeIcon(response.bytes) ?: continue
                        icon = if (decoded.width > ICON_EDGE || decoded.height > ICON_EDGE) {
                            Bitmap.createScaledBitmap(decoded, ICON_EDGE, ICON_EDGE, true)
                        } else decoded
                        if (icon !== decoded) decoded.recycle()
                        storeIcon(context, origin, icon)
                        break
                    }
                }
                SpeedDialSiteMetadata(title, icon)
            }
        }

    /** WebView can supply icons that cannot be decoded from an .ico HTTP response. */
    suspend fun saveVisitedIcon(context: Context, pageUrl: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val origin = origin(pageUrl) ?: return@withContext
        val scaled = if (bitmap.width > ICON_EDGE || bitmap.height > ICON_EDGE) {
            Bitmap.createScaledBitmap(bitmap, ICON_EDGE, ICON_EDGE, true)
        } else bitmap
        storeIcon(context, origin, scaled)
    }

    private fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) null
        else "$scheme://${uri.host.lowercase()}${if (uri.port >= 0) ":${uri.port}" else ""}"
    }.getOrNull()

    private fun iconFile(context: Context, origin: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(context.applicationContext.filesDir, "speed-dial-icons"), "$key.png")
    }

    private fun storeIcon(context: Context, origin: String, bitmap: Bitmap) {
        com.miowser.app.persistence.RestoreWriteFence.write { storeIconUnfenced(context, origin, bitmap) }
    }
    private fun storeIconUnfenced(context: Context, origin: String, bitmap: Bitmap) {
        synchronized(memory) { memory.put(origin, bitmap) }
        val file = iconFile(context, origin)
        file.parentFile?.mkdirs()
        val temporary = runCatching { File.createTempFile(file.name, ".tmp", file.parentFile) }
            .getOrNull() ?: return
        runCatching {
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
            prune(file.parentFile)
        }.onFailure { temporary.delete() }
    }

    private fun prune(directory: File?) {
        val files = directory?.listFiles { file -> file.extension == "png" }
            ?.sortedByDescending(File::lastModified) ?: return
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= MAX_CACHED_ICONS || bytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    private data class HttpResult(val finalUrl: String, val bytes: ByteArray, val charset: Charset)

    private fun readHttp(url: String, maxBytes: Int, htmlOnly: Boolean): HttpResult? {
        if (origin(url) == null) return null
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Miowser")
            if (connection.responseCode !in 200..299) return null
            if (htmlOnly && connection.contentType?.contains("html", ignoreCase = true) == false) return null
            if (!htmlOnly && connection.contentLengthLong > maxBytes) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8 * 1024)
                while (output.size() <= maxBytes) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
            val charset = runCatching {
                val name = Regex("charset=([^; ]+)", RegexOption.IGNORE_CASE)
                    .find(connection.contentType.orEmpty())?.groupValues?.getOrNull(1)
                    ?.trim('"', '\'') ?: "UTF-8"
                Charset.forName(name)
            }.getOrDefault(Charsets.UTF_8)
            if (output.size() > maxBytes && !htmlOnly) null
            else HttpResult(connection.url.toString(), output.toByteArray(), charset)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun iconCandidates(pageUrl: String, html: String?): List<String> {
        val base = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        val linked = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html.orEmpty())
            .mapNotNull { tag ->
                val rel = attribute(tag.value, "rel")?.lowercase() ?: return@mapNotNull null
                if (!rel.contains("icon")) return@mapNotNull null
                val href = attribute(tag.value, "href") ?: return@mapNotNull null
                runCatching { base.resolve(href).toString() }.getOrNull()
            }.filter { origin(it) != null && !it.endsWith(".svg", ignoreCase = true) }
            .take(5).toList()
        val root = origin(pageUrl) ?: return linked
        return (linked + listOf(
            "$root/apple-touch-icon.png", "$root/favicon-32x32.png", "$root/favicon.png", "$root/favicon.ico"
        )).distinct()
    }

    private fun attribute(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*([\\\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(2)

    private fun pageTitle(html: String, pageUrl: String): String? {
        val siteName = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
            .firstNotNullOfOrNull { tag ->
                val key = attribute(tag.value, "property") ?: attribute(tag.value, "name")
                if (key?.equals("og:site_name", true) == true ||
                    key?.equals("application-name", true) == true
                ) attribute(tag.value, "content") else null
            }
        val raw = siteName ?: Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        return raw?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
            ?.replace(Regex("\\s+"), " ")?.trim()?.take(160)
            ?.takeIf { it.isNotBlank() && !it.equals(pageUrl, true) }
    }

    private fun decodeSampled(bytes: ByteArray, offset: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, offset, bytes.size - offset, bounds)
        val sample = com.miowser.app.performance.boundedImageSample(bounds.outWidth, bounds.outHeight, ICON_EDGE * 2)
            ?: return null
        return BitmapFactory.decodeByteArray(bytes, offset, bytes.size - offset,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun decodeIcon(bytes: ByteArray): Bitmap? {
        decodeSampled(bytes, 0)?.let { return it }
        // ICO containers frequently embed a PNG, which BitmapFactory can decode directly.
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val start = (0..bytes.size - signature.size).firstOrNull { index ->
            signature.indices.all { offset -> bytes[index + offset] == signature[offset] }
        } ?: return null
        return decodeSampled(bytes, start)
    }
}
