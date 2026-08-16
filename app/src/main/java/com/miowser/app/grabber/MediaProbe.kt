package com.miowser.app.grabber

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val TAG = "Miowser/Grabber"
private const val CONNECT_TIMEOUT_MS = 6000
private const val READ_TIMEOUT_MS = 6000
private const val MANIFEST_MAX_CHARS = 512 * 1024 // generous cap for a text manifest
private const val DIRECT_FILE_PROBE_RANGE = "bytes=0-0" // headers only (size/type), not the body

/**
 * Verifies a candidate URL by actually fetching it — a manifest's real body for HLS/DASH (magic
 * bytes via [ManifestSniffer], not just the .m3u8/.mpd extension), or just the headers
 * (Content-Length/Content-Type) for direct files, via a 1-byte ranged request so confirming a
 * video exists doesn't mean downloading it. Runs on a small shared background pool; results are
 * always delivered on the main thread so callers can touch Compose state without their own
 * thread-hopping (shouldInterceptRequest, where classification happens, runs off the main
 * thread — see MiowserWebViewClient's own threading note).
 *
 * Forwards the browser's own cookies/User-Agent/Referer so session-gated content verifies
 * correctly instead of getting a 403 a real page load wouldn't have hit — the same concern that
 * mattered for the download-handoff research (project memory: 1dm-browser-downloader-handoff.md).
 *
 * Known limitation: a server that ignores the Range header on the direct-file path will just
 * return the whole file with a 200. Real-world CDNs (including the ones this is tested against)
 * overwhelmingly support ranged requests; a HEAD-first fallback would close this gap but isn't
 * built here.
 *
 * HLS size estimation is ported from 1DM's own approach (reverse-engineered; project memory:
 * 1dm-grabber-mechanics.md, RemoteFile.getApproxLengthFromThreadInfo): rather than downloading a
 * whole quality to know its size, sample the real byte size of exactly one segment (a ranged
 * request, same technique as the direct-file path above) and extrapolate using that segment's own
 * duration against the playlist's total duration, with a 10% safety margin —
 * `estimate = segmentBytes / segmentDurationSeconds * totalDurationSeconds * 1.1`. For a master
 * playlist this means one extra fetch of its best variant's own leaf playlist first, to get to
 * real segment URLs at all. Always surfaced as an estimate (isSizeApproximate), never claimed as
 * exact — 1DM shows the equivalent number with a literal "~" prefix for the same reason.
 */
object MediaProbe {
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun probe(
        url: String,
        pageUrl: String,
        userAgent: String,
        guessedType: MediaType,
        onResult: (DetectedMedia?) -> Unit
    ) {
        executor.execute {
            val result = runCatching { doProbe(url, pageUrl, userAgent, guessedType) }
                .onFailure { Log.d(TAG, "probe failed for $url: ${it.message}") }
                .getOrNull()
            mainHandler.post { onResult(result) }
        }
    }

    private fun doProbe(url: String, pageUrl: String, userAgent: String, guessedType: MediaType): DetectedMedia? {
        val isManifest = MediaUrlClassifier.isManifestType(guessedType)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", pageUrl)
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
            if (!isManifest) setRequestProperty("Range", DIRECT_FILE_PROBE_RANGE)
        }

        try {
            if (connection.responseCode !in 200..299) return null

            return if (isManifest) {
                probeManifest(connection, url, pageUrl, userAgent)
            } else {
                probeDirectFile(connection, url, pageUrl, guessedType)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun probeManifest(connection: HttpURLConnection, url: String, pageUrl: String, userAgent: String): DetectedMedia? {
        val body = connection.inputStream.bufferedReader().readTextUpTo(MANIFEST_MAX_CHARS)
        val info = ManifestSniffer.sniff(body, url)
        val confirmedType = info.confirmedType ?: return null // extension lied — a real page, not a manifest

        // A master playlist has no segments of its own to sample — follow its best variant (the
        // same one already chosen for `resolution`) to its leaf playlist, one more GET, exactly
        // as 1DM resolves a specific quality before estimating that quality's size rather than a
        // mythical "whole stream" size. A leaf playlist already has everything it needs.
        val leafInfo = if (info.isMasterOrMultiVariant) {
            info.bestVariantUrl?.let { fetchLeafPlaylist(it, userAgent, pageUrl) }
        } else {
            info
        }
        val estimatedSizeBytes = leafInfo?.let { estimateSizeFromSegment(it, userAgent, pageUrl) }

        return DetectedMedia(
            url = url,
            pageUrl = pageUrl,
            type = confirmedType,
            isConfirmed = true,
            sizeBytes = estimatedSizeBytes,
            isSizeApproximate = estimatedSizeBytes != null,
            durationSeconds = info.durationSeconds ?: leafInfo?.durationSeconds,
            variantCount = info.variantCount.takeIf { it > 0 },
            resolution = info.resolution,
            isEncrypted = info.isEncrypted,
            pageTitle = null // filled in by Tab, which is the one that actually knows it — see Tab.kt
        )
    }

    /** One extra GET for a master playlist's chosen variant, sniffed the same way as any other manifest. */
    private fun fetchLeafPlaylist(variantUrl: String, userAgent: String, pageUrl: String): ManifestInfo? {
        val connection = (URL(variantUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", pageUrl)
            CookieManager.getInstance().getCookie(variantUrl)?.let { setRequestProperty("Cookie", it) }
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().readTextUpTo(MANIFEST_MAX_CHARS)
            ManifestSniffer.sniff(body, variantUrl)
        } catch (e: Exception) {
            Log.d(TAG, "variant leaf fetch failed for $variantUrl: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun estimateSizeFromSegment(leaf: ManifestInfo, userAgent: String, pageUrl: String): Long? {
        val segmentUrl = leaf.firstSegmentUrl ?: return null
        val segmentDuration = leaf.firstSegmentDurationSeconds?.takeIf { it > 0 } ?: return null
        val totalDuration = leaf.durationSeconds?.takeIf { it > 0 } ?: return null
        val segmentBytes = probeSegmentSize(segmentUrl, userAgent, pageUrl) ?: return null
        return (segmentBytes.toDouble() / segmentDuration * totalDuration * 1.1).toLong()
    }

    /** A ranged probe against exactly one real segment — headers only, same spirit as probeDirectFile. */
    private fun probeSegmentSize(segmentUrl: String, userAgent: String, pageUrl: String): Long? {
        val connection = (URL(segmentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", pageUrl)
            setRequestProperty("Range", DIRECT_FILE_PROBE_RANGE)
            CookieManager.getInstance().getCookie(segmentUrl)?.let { setRequestProperty("Cookie", it) }
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                ?: connection.getHeaderField("Content-Length")?.toLongOrNull()
        } catch (e: Exception) {
            Log.d(TAG, "segment size probe failed for $segmentUrl: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun probeDirectFile(
        connection: HttpURLConnection,
        url: String,
        pageUrl: String,
        guessedType: MediaType
    ): DetectedMedia? {
        val contentType = connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim()
        val expectedPrefix = when (guessedType) {
            MediaType.AUDIO -> "audio/"
            MediaType.SUBTITLE -> "text/"
            else -> "video/"
        }
        // A Content-Type that actively contradicts the extension (e.g. "text/html" for a claimed
        // .mp4 — the classic "fake extension, real error page" case) means this isn't real media.
        // A missing or generic type (application/octet-stream is common) doesn't disqualify it.
        val contradicts = contentType != null &&
            !contentType.startsWith(expectedPrefix) &&
            !contentType.startsWith("application/")
        if (contradicts) return null

        val sizeBytes = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
            ?: connection.getHeaderField("Content-Length")?.toLongOrNull()

        return DetectedMedia(
            url = url,
            pageUrl = pageUrl,
            type = guessedType,
            isConfirmed = contentType != null,
            sizeBytes = sizeBytes,
            isSizeApproximate = false, // Content-Length/-Range is an exact, server-reported size
            durationSeconds = null,
            variantCount = null,
            resolution = null, // not derivable from headers alone; would need to parse the container itself
            isEncrypted = false,
            pageTitle = null // filled in by Tab, which is the one that actually knows it — see Tab.kt
        )
    }

    private fun parseContentRangeTotal(headerValue: String?): Long? =
        headerValue?.substringAfter('/', missingDelimiterValue = "")?.toLongOrNull()

    private fun BufferedReader.readTextUpTo(maxChars: Int): String {
        val buffer = CharArray(maxChars)
        val charsRead = read(buffer, 0, maxChars)
        return if (charsRead > 0) String(buffer, 0, charsRead) else ""
    }
}
