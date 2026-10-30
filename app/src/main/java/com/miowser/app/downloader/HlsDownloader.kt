package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import com.miowser.app.grabber.HlsPlaylistParser
import com.miowser.app.grabber.HlsSegment
import com.miowser.app.grabber.HlsByteRange
import com.miowser.app.grabber.HlsInitialization
import com.miowser.app.grabber.ManifestSniffer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "Miowser/Downloader"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

/** HLS segment downloading with a durable, verified cache that survives process death. */
object HlsDownloader {
    private val segmentExecutor = Executors.newFixedThreadPool(4)

    fun run(context: Context, download: Download) {
        val recoveryDir = DownloadRecoveryStore.directory(context, download.id)
        try {
            download.enterStage(DownloadStatus.DOWNLOADING)
            download.awaitIfPaused()
            if (download.isCanceled) return cancel(context, download)

            val requestedBody = fetchText(download.sourceUrl, download)
                ?: return fail(download, "Could not fetch playlist")
            val sniffed = ManifestSniffer.sniff(requestedBody, download.sourceUrl)
            val (leafBody, leafUrl) = if (sniffed.isMasterOrMultiVariant) {
                val variantUrl = sniffed.bestVariantUrl
                    ?: return fail(download, "Master playlist has no usable variant")
                val variantBody = fetchText(variantUrl, download)
                    ?: return fail(download, "Could not fetch variant playlist")
                variantBody to variantUrl
            } else {
                requestedBody to download.sourceUrl
            }

            val playlist = HlsPlaylistParser.parseLeaf(leafBody, leafUrl)
            playlist.unsupportedReason?.let { return fail(download, it) }
            if (playlist.segments.isEmpty()) return fail(download, "Playlist has no segments")
            val initializations = playlist.segments.mapNotNull { it.initialization }.distinct()
            if (initializations.size > 1) {
                return fail(download, "This HLS stream changes its initialization segment; it cannot be safely joined yet")
            }
            val finalFileName = "${download.outputBaseName}.mp4"
            val fingerprint = recoveryFingerprint(playlist.segments.map(::segmentIdentity) + initializations.map(::initializationIdentity))
            val existing = DownloadRecoveryStore.load(context, download.id)
            val reusable = existing?.takeIf {
                it.transferKind == RecoveryTransferKind.HLS_SEGMENTS &&
                    it.itemFingerprint == fingerprint &&
                    it.itemCount == playlist.segments.size &&
                    it.outputFileName == finalFileName
            }
            if (existing != null && reusable == null) DownloadRecoveryStore.clear(context, download.id)
            recoveryDir.mkdirs()
            val recoverySession = DownloadRecoverySession(
                context,
                reusable ?: DownloadRecoveryPlan(
                    downloadId = download.id,
                    transferKind = RecoveryTransferKind.HLS_SEGMENTS,
                    sourceFingerprint = recoveryFingerprint(listOf(leafUrl, leafBody)),
                    etag = null,
                    lastModified = null,
                    totalLength = -1L,
                    outputFileName = finalFileName,
                    mimeType = "video/mp4",
                    itemFingerprint = fingerprint,
                    itemCount = playlist.segments.size
                )
            )

            val segmentFiles = downloadAllSegments(context, playlist.segments, download, recoverySession)
                ?: return if (download.isCanceled) cancel(context, download) else Unit
            if (download.isCanceled) return cancel(context, download)

            val initialization = initializations.singleOrNull()
            val initializationFile = initialization?.let { map ->
                File(recoveryDir, "initialization.part").also { file ->
                    streamResource(map.url, map.byteRange, map.keyUri, map.ivHex, 0L, file, download)
                }
            }

            download.enterStage(DownloadStatus.MUXING)
            download.awaitIfPaused()
            if (download.isCanceled) return cancel(context, download)
            val muxedFile = File(recoveryDir, "muxed.mp4")
            try {
                if (initializationFile != null) {
                    val combined = File(recoveryDir, "combined-fragments.mp4")
                    combined.outputStream().buffered().use { output ->
                        initializationFile.inputStream().buffered().use { it.copyPausableTo(output, download) }
                        segmentFiles.forEach { file -> file.inputStream().buffered().use { it.copyPausableTo(output, download) } }
                    }
                    SegmentMuxer.mux(listOf(combined), muxedFile.absolutePath, listOf(playlist.totalDurationSeconds), download::checkTransferActive)
                } else {
                    try {
                        SegmentMuxer.mux(segmentFiles, muxedFile.absolutePath,
                            playlist.segments.map { it.durationSeconds }, download::checkTransferActive)
                    } catch (firstFailure: SegmentMuxer.MuxException) {
                        // Some TS servers omit enough headers from an individual chunk that
                        // MediaExtractor only recognizes the joined transport stream.
                        if (!firstFailure.message.orEmpty().contains("isn't a readable media container") ||
                            segmentFiles.firstOrNull()?.let(::isTransportStream) != true) throw firstFailure
                        muxedFile.delete()
                        val joined = File(recoveryDir, "combined-transport.ts")
                        joined.outputStream().buffered().use { output ->
                            segmentFiles.forEach { file -> file.inputStream().buffered().use { it.copyPausableTo(output, download) } }
                        }
                        SegmentMuxer.mux(listOf(joined), muxedFile.absolutePath,
                            listOf(playlist.totalDurationSeconds), download::checkTransferActive)
                    }
                }
            } catch (e: SegmentMuxer.MuxException) {
                return fail(download, e.message ?: "Muxing failed")
            }

            if (download.isCanceled) return cancel(context, download)
            download.displayName = finalFileName
            download.checkTransferActive()
            val uri = MediaStoreOutput.publishFile(context, muxedFile, finalFileName, "video/mp4", download::checkTransferActive)
                ?: return fail(download, "Could not create output file")
            MediaStoreOutput.actualFileName(context, uri)?.let { download.displayName = it }
            download.savedUri = uri
            download.status = DownloadStatus.COMPLETED
            DownloadRecoveryStore.clear(context, download.id)
        } catch (e: Exception) {
            download.awaitIfPaused()
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "HLS download failed for ${download.sourceUrl}: ${e.message}")
            if (download.isCanceled) cancel(context, download) else fail(download, e.message ?: "Unknown error")
        }
    }

    /** Existing verified files are counted immediately; workers are started only for missing items. */
    private fun downloadAllSegments(
        context: Context,
        segments: List<HlsSegment>,
        download: Download,
        recoverySession: DownloadRecoverySession
    ): List<File>? {
        val files = segments.indices.map { DownloadRecoveryStore.itemFile(context, download.id, it) }
        // One digest pass per existing item, reused for counts and missing work.
        val verified = files.map { download.checkTransferActive(); DownloadRecoveryStore.isVerifiedItem(it, verifyDigest = true) }
        val completed = AtomicInteger(verified.count { it })
        download.totalBytes = segments.size.toLong()
        download.bytesDownloaded = completed.get().toLong()
        val missing = files.indices.filterNot { verified[it] }
        boundedSegmentWork(missing, segmentExecutor, checkActive = download::checkTransferActive) { index ->
            val target = files[index]
            val temporary = File(target.parentFile, "${target.name}.tmp")
            try {
                temporary.delete()
                downloadSegment(segments[index], temporary, download)
                download.checkTransferActive()
                DownloadRecoveryStore.commitVerifiedItem(temporary, target)
                completed.incrementAndGet()
                download.addProgress(1L)
                recoverySession.updateElapsed(download.elapsedActiveMillis())
            } finally { temporary.delete() }
        }
        return files
    }

    private fun downloadSegment(segment: HlsSegment, outputFile: File, download: Download) {
        streamResource(segment.url, segment.byteRange, segment.keyUri, segment.ivHex,
            segment.sequenceNumber, outputFile, download)
        val prefix = outputFile.inputStream().use { input ->
            val bytes = ByteArray(4096)
            val count = input.read(bytes)
            bytes.copyOf(count.coerceAtLeast(0))
        }
        check(looksLikeMedia(prefix, segment.initialization != null)) {
            "HLS segment ${segment.sequenceNumber} returned non-media data; check the source link or site access"
        }
    }

    private fun streamResource(url: String, range: HlsByteRange?, keyUri: String?, ivHex: String?,
        sequence: Long, output: File, download: Download) {
        // Fetch the small key before taking a media connection; avoid nested permit deadlock.
        val cipher = keyUri?.let { keyUrl ->
            val key = fetchBytes(keyUrl, download, maximumBytes = 16)
                ?: error("Could not fetch decryption key")
            HlsAes128Decryptor.cipher(key, ivHex, sequence)
        }
        val connection = openConnection(url, download, range)
        try {
            validateResponse(connection, range)
            var received = 0L
            connection.inputStream.use { input ->
                output.outputStream().buffered(64 * 1024).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        download.checkTransferActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(range == null || received <= range.length) { "HLS range exceeded declared length" }
                        if (cipher == null) target.write(buffer, 0, count)
                        else cipher.update(buffer, 0, count)?.let(target::write)
                    }
                    cipher?.doFinal()?.let(target::write)
                }
            }
            require(range == null || received == range.length) { "HLS byte-range response was incomplete" }
            val expected = connection.contentLengthLong
            require(expected < 0 || received == expected) { "HLS resource ended early" }
        } finally { download.closeConnection(connection) }
    }

    private fun segmentIdentity(segment: HlsSegment): String = listOf(
        segment.url,
        segment.keyUri.orEmpty(),
        segment.ivHex.orEmpty(),
        segment.sequenceNumber.toString(),
        segment.byteRange?.let { "${it.start}:${it.length}" }.orEmpty(),
        segment.initialization?.let(::initializationIdentity).orEmpty()
    ).joinToString("|")

    private fun initializationIdentity(map: HlsInitialization): String = listOf(
        map.url, map.byteRange?.let { "${it.start}:${it.length}" }.orEmpty(),
        map.keyUri.orEmpty(), map.ivHex.orEmpty()
    ).joinToString("|")

    private fun fetchBytes(url: String, download: Download, range: HlsByteRange? = null, maximumBytes: Int = 4 * 1024 * 1024): ByteArray? {
        val connection = openConnection(url, download, range)
        return try {
            validateResponse(connection, range)
            connection.inputStream.use { it.readBytesPausable(download, maximumBytes) }
                .also { requireExpectedRangeLength(it, range) }
        } finally {
            download.closeConnection(connection)
        }
    }

    private fun fetchText(url: String, download: Download): String? {
        val connection = openConnection(url, download)
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytesPausable(download).toString(Charsets.UTF_8) }
        } finally {
            download.closeConnection(connection)
        }
    }

    private fun openConnection(url: String, download: Download, range: HlsByteRange? = null): HttpURLConnection =
        download.openConnection(URL(url)).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", download.userAgent)
            setRequestProperty("Referer", download.referer)
            download.cookies?.let { setRequestProperty("Cookie", it) }
            range?.let { setRequestProperty("Range", "bytes=${it.start}-${it.endInclusive}") }
        }

    private fun validateResponse(connection: HttpURLConnection, range: HlsByteRange?) {
        val status = connection.responseCode
        if (range == null) {
            if (status !in 200..299) throw Exception("HLS resource HTTP $status")
        } else {
            if (status != 206) throw Exception("HLS byte-range request returned HTTP $status")
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()
            if (!contentRange.startsWith("bytes ${range.start}-${range.endInclusive}/")) {
                throw Exception("HLS server returned the wrong byte range")
            }
        }
    }

    private fun requireExpectedRangeLength(bytes: ByteArray, range: HlsByteRange?) {
        if (range != null && bytes.size.toLong() != range.length) {
            throw Exception("HLS byte-range response was incomplete")
        }
    }

    private fun isTransportStream(file: File): Boolean = file.inputStream().use { input ->
        val prefix = ByteArray(4096)
        val count = input.read(prefix)
        count > 0 && looksLikeTransportStream(prefix.copyOf(count))
    }

    private fun looksLikeTransportStream(bytes: ByteArray): Boolean {
        val lastStart = minOf(2048, bytes.size - 377)
        if (lastStart < 0) return bytes.firstOrNull() == 0x47.toByte()
        return (0..lastStart).any { offset ->
            bytes[offset] == 0x47.toByte() && bytes[offset + 188] == 0x47.toByte() &&
                bytes[offset + 376] == 0x47.toByte()
        }
    }

    private fun looksLikeMedia(bytes: ByteArray, fragmentedMp4: Boolean): Boolean {
        if (bytes.isEmpty()) return false
        if (fragmentedMp4) {
            if (bytes.size < 8) return false
            val box = String(bytes, 4, 4, Charsets.US_ASCII)
            return box == "moof" || box == "styp" || box == "sidx"
        }
        return looksLikeTransportStream(bytes) ||
            (bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) in setOf("ftyp", "moof", "styp"))
    }

    private fun cancel(context: Context, download: Download) {
        if (!download.preserveRecoveryOnCancel) DownloadRecoveryStore.clear(context, download.id)
        download.status = DownloadStatus.CANCELED
    }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }
}
