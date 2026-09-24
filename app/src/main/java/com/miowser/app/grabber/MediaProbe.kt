package com.miowser.app.grabber

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import com.miowser.app.downloader.DownloadIdentityInput
import com.miowser.app.downloader.DownloadIdentityResolver
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val TAG = "Miowser/Grabber"
private const val CONNECT_TIMEOUT_MS = 6000
private const val READ_TIMEOUT_MS = 6000
private const val MANIFEST_MAX_CHARS = 512 * 1024 // generous cap for a text manifest
private const val HEADER_ONLY_PROBE_RANGE = "bytes=0-0"
// Duration metadata is commonly near the start, but some MP4/WebM writers place it later.
// Two MiB remains bounded while covering far more real files than the earlier 512 KiB window.
private const val DIRECT_SAMPLE_BYTES = 2 * 1024 * 1024
private const val DIRECT_SAMPLE_RANGE = "bytes=0-${DIRECT_SAMPLE_BYTES - 1}"

/**
 * Verifies a candidate URL by actually fetching it — a manifest's real body for HLS/DASH (magic
 * bytes via [ManifestSniffer], not just the .m3u8/.mpd extension), or HEAD followed by a strictly
 * bounded 2 MiB ranged GET for direct files when headers alone are inconclusive or container metadata
 * can be enriched. Runs on a small
 * shared background pool; results are
 * always delivered on the main thread so callers can touch Compose state without their own
 * thread-hopping (shouldInterceptRequest, where classification happens, runs off the main
 * thread — see MiowserWebViewClient's own threading note).
 *
 * Forwards the browser's own cookies/User-Agent/Referer so session-gated content verifies
 * correctly instead of getting a 403 a real page load wouldn't have hit — the same concern that
 * mattered for the download-handoff research (project memory: 1dm-browser-downloader-handoff.md).
 *
 * HLS size estimation is ported from 1DM's own approach (reverse-engineered; project memory:
 * 1dm-grabber-mechanics.md, RemoteFile.getApproxLengthFromThreadInfo): rather than downloading a
 * whole quality to know its size, sample up to three representative real segments and extrapolate
 * their duration-weighted byte rate against the playlist's total duration. Explicit HLS byte
 * ranges produce an exact size without probes; advertised bandwidth is the final fallback. For a master
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
        if (!isManifest) return probeDirectFile(url, pageUrl, userAgent, guessedType)

        val connection = openAuthenticatedConnection(url, pageUrl, userAgent, "GET")

        try {
            if (connection.responseCode !in 200..299) return null

            return probeManifest(connection, url, pageUrl, userAgent)
        } finally {
            connection.disconnect()
        }
    }

    private fun probeManifest(connection: HttpURLConnection, url: String, pageUrl: String, userAgent: String): DetectedMedia? {
        val body = connection.inputStream.bufferedReader().readTextUpTo(MANIFEST_MAX_CHARS)
        val finalUrl = connection.url.toString()
        val info = ManifestSniffer.sniff(body, finalUrl)
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
        val size = resolveManifestSize(info, leafInfo, userAgent, pageUrl)
        val duration = info.durationSeconds ?: leafInfo?.durationSeconds
        val bitrate = info.bitrateBitsPerSecond ?: leafInfo?.bitrateBitsPerSecond

        return DetectedMedia(
            url = url,
            pageUrl = pageUrl,
            type = confirmedType,
            isConfirmed = true,
            sizeBytes = size.bytes,
            isSizeApproximate = size.isApproximate,
            durationSeconds = duration,
            variantCount = info.variantCount.takeIf { it > 0 },
            resolution = info.resolution,
            isEncrypted = info.isEncrypted,
            pageTitle = null, // filled in by Tab, which is the one that actually knows it — see Tab.kt
            mimeType = connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim(),
            bitrateBitsPerSecond = bitrate,
            finalUrl = finalUrl
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

    private fun resolveManifestSize(
        manifest: ManifestInfo,
        leaf: ManifestInfo?,
        userAgent: String,
        pageUrl: String
    ): ManifestSize {
        val selected = leaf ?: manifest
        val totalDuration = manifest.durationSeconds ?: selected.durationSeconds

        val samples = selected.sampleSegments.mapNotNull { sample ->
            val duration = sample.durationSeconds.takeIf { it > 0.0 } ?: return@mapNotNull null
            val bytes = sample.declaredByteLength
                ?: probeSegmentSize(sample.url, userAgent, pageUrl)
                ?: return@mapNotNull null
            bytes to duration
        }
        val bitrate = manifest.bitrateBitsPerSecond ?: selected.bitrateBitsPerSecond
        val estimate = estimateManifestSize(totalDuration, selected.declaredSizeBytes, bitrate, samples)
        return ManifestSize(estimate.bytes, estimate.isApproximate)
    }

    /** A ranged probe against exactly one real segment — headers only, same spirit as probeDirectFile. */
    private fun probeSegmentSize(segmentUrl: String, userAgent: String, pageUrl: String): Long? {
        val connection = (URL(segmentUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", pageUrl)
            setRequestProperty("Range", HEADER_ONLY_PROBE_RANGE)
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
        url: String,
        pageUrl: String,
        userAgent: String,
        guessedType: MediaType
    ): DetectedMedia? {
        var headSize: Long? = null
        var headDisposition: String? = null
        var headResult: DetectedMedia? = null
        val head = openAuthenticatedConnection(url, pageUrl, userAgent, "HEAD")
        try {
            val statusCode = head.responseCode
            val contentType = head.getHeaderField("Content-Type")
            headDisposition = head.getHeaderField("Content-Disposition")
            headSize = exactProbeSize(
                statusCode = statusCode,
                isHead = true,
                contentRange = head.getHeaderField("Content-Range"),
                contentLength = head.contentLengthLong
            )
            when (decideHeadProbe(statusCode, contentType, guessedType)) {
                HeadProbeAction.REJECT -> return null
                HeadProbeAction.ACCEPT -> {
                    val confirmedType = MediaUrlClassifier.classifyByMimeType(contentType) ?: guessedType
                    val identity = DownloadIdentityResolver.resolve(
                        DownloadIdentityInput(
                            originalUrl = url,
                            finalUrl = head.url.toString(),
                            contentDisposition = headDisposition,
                            declaredMimeType = contentType,
                            browserSuggestedName = null,
                            fallbackBaseName = "media",
                            signature = byteArrayOf()
                        )
                    )
                    headResult = directResult(
                        url = url,
                        pageUrl = pageUrl,
                        confirmedType = confirmedType,
                        sizeBytes = headSize,
                        responseFileName = identity.fileName,
                        mimeType = identity.mimeType,
                        finalUrl = head.url.toString()
                    )
                    // Subtitles have no duration/dimensions to enrich. Video/audio continue into
                    // the bounded GET so MP4 metadata and a real body signature can be collected.
                    if (confirmedType == MediaType.SUBTITLE) return headResult
                }
                HeadProbeAction.SAMPLE_WITH_GET -> Unit
            }
        } catch (e: Exception) {
            Log.d(TAG, "HEAD probe failed for $url; trying bounded GET: ${e.message}")
        } finally {
            head.disconnect()
        }

        val get = openAuthenticatedConnection(url, pageUrl, userAgent, "GET", DIRECT_SAMPLE_RANGE)
        try {
            val statusCode = get.responseCode
            if (statusCode !in 200..299) return headResult
            val contentType = get.getHeaderField("Content-Type")
            val contentDisposition = get.getHeaderField("Content-Disposition") ?: headDisposition
            val finalUrl = get.url.toString()
            val contentRange = get.getHeaderField("Content-Range")
            val contentLength = get.contentLengthLong
            val input = get.inputStream
            val sample = try {
                input.readAtMost(DIRECT_SAMPLE_BYTES)
            } finally {
                // Disconnect before close. Some HTTP stacks try to drain a response on close so
                // the socket can be reused; disconnecting first makes the 2 MiB cap a transfer
                // boundary even when a server ignored Range and replied 200 with the whole file.
                get.disconnect()
                runCatching { input.close() }
            }
            if (sample.isEmpty()) return null
            val signatureExtension = DownloadIdentityResolver.sniffExtension(sample)
            // The shared Step 2 resolver gets the same real response metadata and sampled bytes as
            // a later download. Its result is useful only alongside body/MIME evidence here: a URL
            // suffix by itself must never turn an HTML error page into "confirmed media".
            val identity = DownloadIdentityResolver.resolve(
                DownloadIdentityInput(
                    originalUrl = url,
                    finalUrl = finalUrl,
                    contentDisposition = contentDisposition,
                    declaredMimeType = contentType,
                    browserSuggestedName = null,
                    fallbackBaseName = "media",
                    signature = sample
                )
            )
            val evidenceMimeType = if (signatureExtension != null) identity.mimeType else contentType
            val confirmedType = resolveSampledMediaType(
                guessedType = guessedType,
                contentType = evidenceMimeType,
                signatureExtension = signatureExtension
            ) ?: if (headResult != null && signatureExtension == null && isInconclusiveProbeMimeType(contentType)) {
                return headResult
            } else {
                return null
            }
            val sizeBytes = exactProbeSize(
                statusCode = statusCode,
                isHead = false,
                contentRange = contentRange,
                contentLength = contentLength
            ) ?: headSize
            var metadata = if (confirmedType in isoBmffTypes) IsoBmffMetadataParser.parse(sample) else null
            if (metadata == null && confirmedType in isoBmffTypes && sizeBytes != null && sizeBytes > sample.size) {
                val tail = fetchTailSample(url, pageUrl, userAgent, sizeBytes)
                if (tail != null) metadata = IsoBmffMetadataParser.parse(tail)
            }
            var duration = metadata?.durationSeconds
            if (duration == null && confirmedType in matroskaTypes) {
                duration = MatroskaMetadataParser.durationSeconds(sample)
                if (duration == null && sizeBytes != null && sizeBytes > sample.size) {
                    fetchTailSample(url, pageUrl, userAgent, sizeBytes)?.let {
                        duration = MatroskaMetadataParser.durationSeconds(it)
                    }
                }
            }
            return directResult(
                url = url,
                pageUrl = pageUrl,
                confirmedType = confirmedType,
                sizeBytes = sizeBytes,
                responseFileName = identity.fileName,
                mimeType = identity.mimeType,
                finalUrl = finalUrl,
                durationSeconds = duration,
                resolution = metadata?.resolution
            )
        } catch (e: Exception) {
            Log.d(TAG, "bounded GET probe failed for $url: ${e.message}")
            return headResult
        } finally {
            // Explicit disconnect after the capped read is what prevents a Range-ignoring HTTP
            // 200 server from continuing to transfer the rest of a large file.
            get.disconnect()
        }
    }

    private fun directResult(
        url: String,
        pageUrl: String,
        confirmedType: MediaType,
        sizeBytes: Long?,
        responseFileName: String?,
        mimeType: String?,
        finalUrl: String?,
        durationSeconds: Double? = null,
        resolution: String? = null
    ) = DetectedMedia(
            url = url,
            pageUrl = pageUrl,
            type = confirmedType,
            isConfirmed = true,
            sizeBytes = sizeBytes,
            isSizeApproximate = false,
            durationSeconds = durationSeconds,
            variantCount = null,
            resolution = resolution,
            isEncrypted = false,
            pageTitle = null,
            responseFileName = responseFileName,
            mimeType = mimeType,
            finalUrl = finalUrl
        )

    private fun fetchTailSample(
        url: String,
        pageUrl: String,
        userAgent: String,
        totalBytes: Long
    ): ByteArray? {
        val start = (totalBytes - DIRECT_SAMPLE_BYTES).coerceAtLeast(0L)
        val end = totalBytes - 1L
        if (end < start) return null
        val connection = openAuthenticatedConnection(url, pageUrl, userAgent, "GET", "bytes=$start-$end")
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) return null
            val returned = parseProbeContentRange(connection.getHeaderField("Content-Range")) ?: return null
            if (returned.start != start || returned.endInclusive != end || returned.total != totalBytes) return null
            val input = connection.inputStream
            try {
                input.readAtMost(DIRECT_SAMPLE_BYTES)
            } finally {
                connection.disconnect()
                runCatching { input.close() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "tail metadata probe failed for $url: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openAuthenticatedConnection(
        url: String,
        pageUrl: String,
        userAgent: String,
        method: String,
        range: String? = null
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        requestMethod = method
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)
        setRequestProperty("Referer", pageUrl)
        CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        range?.let { setRequestProperty("Range", it) }
    }

    private fun parseContentRangeTotal(headerValue: String?): Long? =
        parseProbeContentRangeTotal(headerValue)

    private data class ManifestSize(val bytes: Long?, val isApproximate: Boolean)

    private val isoBmffTypes = setOf(MediaType.MP4)
    private val matroskaTypes = setOf(MediaType.WEBM, MediaType.MATROSKA)

    private fun BufferedReader.readTextUpTo(maxChars: Int): String {
        val buffer = CharArray(maxChars)
        val charsRead = read(buffer, 0, maxChars)
        return if (charsRead > 0) String(buffer, 0, charsRead) else ""
    }
}
