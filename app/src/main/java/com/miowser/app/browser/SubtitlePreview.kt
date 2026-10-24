package com.miowser.app.browser

import android.webkit.CookieManager
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.player.mediaReferer
import com.miowser.app.network.ConnectionBudget
import com.miowser.app.network.NativeConnections
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val MAX_SUBTITLE_PREVIEW_BYTES = 512 * 1024
private const val MAX_SUBTITLE_PLAYBACK_BYTES = 4 * 1024 * 1024

internal data class SubtitlePreview(val text: String, val truncated: Boolean)

/** Fetches text only while the viewer is open; never retains the subtitle in the detector list. */
internal suspend fun loadSubtitlePreview(item: DetectedMedia, userAgent: String): SubtitlePreview =
    loadSubtitleText(item, userAgent, MAX_SUBTITLE_PREVIEW_BYTES)

/** Uses the same authenticated request as Inspect for complete VTT or SRT playback. */
internal suspend fun loadSubtitleForPlayback(item: DetectedMedia, userAgent: String): String {
    val result = loadSubtitleText(item, userAgent, MAX_SUBTITLE_PLAYBACK_BYTES)
    require(!result.truncated) { "This subtitle is too large to play." }
    require(result.text.trimStart().startsWith("WEBVTT") ||
        Regex("(?m)^\\d{2}:\\d{2}:\\d{2},\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2},\\d{3}").containsMatchIn(result.text)) {
        "This link did not return a WebVTT or SRT subtitle."
    }
    return result.text
}

private suspend fun loadSubtitleText(item: DetectedMedia, userAgent: String, limit: Int): SubtitlePreview =
    withContext(Dispatchers.IO) {
        val requestContext = currentCoroutineContext()
        var target = URL(item.url)
        repeat(6) { redirectCount ->
            requestContext.ensureActive()
            require(target.protocol in setOf("http", "https") && target.userInfo == null) {
                "This subtitle link cannot be previewed."
            }
            val connection = NativeConnections.open(target, ConnectionBudget.Priority.PLAYBACK,
                checkActive = { requestContext.ensureActive() }, owner = requestContext).apply {
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/vtt, text/plain, */*")
                mediaReferer(item.pageUrl, target.toString(), item.requestReferer)
                    ?.let { setRequestProperty("Referer", it) }
                item.requestOrigin?.let { setRequestProperty("Origin", it) }
                CookieManager.getInstance().getCookie(target.toString())
                    ?.let { setRequestProperty("Cookie", it) }
            }
            try {
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    if (redirectCount == 5) error("Too many subtitle redirects.")
                    val destination = connection.getHeaderField("Location")
                        ?: error("Subtitle redirect has no destination.")
                    val next = URL(target, destination)
                    require(!(target.protocol == "https" && next.protocol == "http")) {
                        "Insecure subtitle redirect blocked."
                    }
                    target = next
                } else {
                    if (status !in 200..299) error("Subtitle request failed (HTTP $status).")
                    val (bytes, truncated) = connection.inputStream.use { readSubtitlePreview(it, limit) }
                    require(bytes.none { it == 0.toByte() }) { "This link did not return a text subtitle." }
                    val text = bytes.toString(Charsets.UTF_8)
                        .removePrefix("\uFEFF")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                    require(text.isNotBlank()) { "This subtitle is empty." }
                    return@withContext SubtitlePreview(text, truncated)
                }
            } finally {
                NativeConnections.close(connection)
            }
        }
        error("Too many subtitle redirects.")
    }

internal suspend fun readSubtitlePreview(input: InputStream, limit: Int = MAX_SUBTITLE_PREVIEW_BYTES): Pair<ByteArray, Boolean> {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        val remaining = limit - output.size()
        if (read > remaining) {
            if (remaining > 0) output.write(buffer, 0, remaining)
            return output.toByteArray() to true
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray() to false
}
