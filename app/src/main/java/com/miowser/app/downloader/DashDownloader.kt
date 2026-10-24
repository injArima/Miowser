package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import com.miowser.app.grabber.DashByteRange
import com.miowser.app.grabber.DashPlaylistParser
import com.miowser.app.grabber.DashSegment
import com.miowser.app.grabber.DashTrack
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
private const val BUFFER_SIZE = 64 * 1024
private const val MAX_INDEX_RANGE_BYTES = 8 * 1024 * 1024L

/**
 * DASH pipeline for SegmentTemplate, SegmentList, and single-file SegmentBase representations.
 * SegmentBase is resolved only after the best audio/video representations are selected: the
 * declared index range is fetched, parsed as an ISO-BMFF sidx box, and converted into concrete
 * byte-range media segments. Every segment is streamed into its own cache file rather than held
 * in RAM; init + media files are then concatenated into one valid fragmented-MP4 track and the
 * existing muxer combines separate video/audio tracks.
 */
object DashDownloader {
    private val segmentExecutor = Executors.newFixedThreadPool(4)

    fun run(context: Context, download: Download) {
        val recoveryDir = DownloadRecoveryStore.directory(context, download.id)
        try {
            download.enterStage(DownloadStatus.DOWNLOADING)
            download.awaitIfPaused()
            if (download.isCanceled) return markCanceled(context, download)

            val body = fetchText(download.sourceUrl, download) ?: return fail(download, "Could not fetch manifest")
            val manifest = DashPlaylistParser.parse(body, download.sourceUrl)
            manifest.unsupportedReason?.let { return fail(download, it) }

            val selectedVideo = manifest.tracks.filter { it.type == "video" }.maxByOrNull { it.bandwidth ?: 0L }
            val selectedAudio = manifest.tracks.filter { it.type == "audio" }.maxByOrNull { it.bandwidth ?: 0L }
            if (selectedVideo == null && selectedAudio == null) {
                return fail(download, "No downloadable video/audio track found in this MPD")
            }

            val videoTrack = selectedVideo?.let { resolveSegmentBase(it, download) }
            val audioTrack = selectedAudio?.let { resolveSegmentBase(it, download) }
            val videoCount = videoTrack?.mediaSegments?.size ?: 0
            val audioCount = audioTrack?.mediaSegments?.size ?: 0
            download.totalBytes = (videoCount + audioCount).toLong()
            if (download.totalBytes <= 0L) return fail(download, "Selected DASH tracks contain no media segments")

            val finalFileName = "${download.outputBaseName}.mp4"
            val fingerprint = recoveryFingerprint(buildList {
                videoTrack?.let { addAll(trackIdentity("video", it)) }
                audioTrack?.let { addAll(trackIdentity("audio", it)) }
            })
            val existing = DownloadRecoveryStore.load(context, download.id)
            val reusable = existing?.takeIf {
                it.transferKind == RecoveryTransferKind.DASH_SEGMENTS &&
                    it.itemFingerprint == fingerprint &&
                    it.itemCount == videoCount + audioCount &&
                    it.outputFileName == finalFileName
            }
            if (existing != null && reusable == null) DownloadRecoveryStore.clear(context, download.id)
            recoveryDir.mkdirs()
            val recoverySession = DownloadRecoverySession(
                context,
                reusable ?: DownloadRecoveryPlan(
                    downloadId = download.id,
                    transferKind = RecoveryTransferKind.DASH_SEGMENTS,
                    sourceFingerprint = recoveryFingerprint(listOf(download.sourceUrl, body)),
                    etag = null,
                    lastModified = null,
                    totalLength = -1L,
                    outputFileName = finalFileName,
                    mimeType = "video/mp4",
                    itemFingerprint = fingerprint,
                    itemCount = videoCount + audioCount
                )
            )
            val verified = (0 until videoCount + audioCount).filterTo(mutableSetOf()) { index ->
                download.checkTransferActive()
                DownloadRecoveryStore.isVerifiedItem(
                    DownloadRecoveryStore.itemFile(context, download.id, index), verifyDigest = true)
            }
            val completedCount = AtomicInteger(verified.size)
            download.bytesDownloaded = completedCount.get().toLong()

            val videoFile = videoTrack?.let {
                downloadTrack(context, it, recoveryDir, "video", 0, download, completedCount, recoverySession, verified)
            }
            if (download.isCanceled) return markCanceled(context, download)
            val audioFile = audioTrack?.let {
                downloadTrack(context, it, recoveryDir, "audio", videoCount, download, completedCount, recoverySession, verified)
            }
            if (download.isCanceled) return markCanceled(context, download)
            if (videoFile == null && audioFile == null) return fail(download, "Segment download failed")

            download.enterStage(DownloadStatus.MUXING)
            download.awaitIfPaused()
            if (download.isCanceled) return markCanceled(context, download)
            val muxedFile = File(recoveryDir, "muxed.mp4")
            try {
                SegmentMuxer.muxTracks(videoFile, audioFile, muxedFile.absolutePath, download::checkTransferActive)
            } catch (e: SegmentMuxer.MuxException) {
                return fail(download, e.message ?: "Muxing failed")
            }

            if (download.isCanceled) return markCanceled(context, download)
            download.displayName = finalFileName
            download.checkTransferActive()
            val uri = MediaStoreOutput.publishFile(context, muxedFile, finalFileName, "video/mp4", download::checkTransferActive)
                ?: return fail(download, "Could not create output file")
            MediaStoreOutput.legacyFileName(uri)?.let { download.displayName = it }
            download.savedUri = uri
            download.status = DownloadStatus.COMPLETED
            DownloadRecoveryStore.clear(context, download.id)
        } catch (e: DashDownloadException) {
            download.awaitIfPaused()
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "DASH download rejected for ${download.sourceUrl}: ${e.message}")
            if (download.isCanceled) markCanceled(context, download)
            else fail(download, e.message ?: "Unsupported DASH structure")
        } catch (e: Exception) {
            download.awaitIfPaused()
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "DASH download failed for ${download.sourceUrl}: ${e.message}")
            if (download.isCanceled) markCanceled(context, download)
            else fail(download, e.message ?: "Unknown error")
        }
    }

    private fun resolveSegmentBase(track: DashTrack, download: Download): DashTrack {
        val segmentBase = track.segmentBase ?: return track
        segmentBase.validationError?.let { throw DashDownloadException(it) }
        val indexRange = segmentBase.indexRange
            ?: throw DashDownloadException("SegmentBase is missing a valid index range")
        if (segmentBase.indexUrl != segmentBase.mediaUrl) {
            throw DashDownloadException("External SegmentBase index files are not supported")
        }
        val indexBytes = fetchExactRange(
            segmentBase.indexUrl,
            indexRange,
            MAX_INDEX_RANGE_BYTES,
            download,
            "DASH index"
        )
        val index = when (val parsed = Mp4SidxParser.parse(indexBytes, indexRange.start)) {
            is SidxParseResult.Success -> parsed.index
            is SidxParseResult.Failure -> throw DashDownloadException(parsed.message)
        }
        segmentBase.expectedTimescale?.let { expected ->
            if (expected != index.timescale) {
                throw DashDownloadException("SegmentBase timescale does not match its sidx index")
            }
        }

        val initializationRange = segmentBase.initializationRange ?: when {
            segmentBase.initializationUrl != segmentBase.mediaUrl -> null
            indexRange.start > 0L -> DashByteRange(0L, indexRange.start - 1L)
            else -> throw DashDownloadException("SegmentBase is missing an initialization range")
        }
        return track.copy(
            initSegmentUrl = segmentBase.initializationUrl,
            initSegmentRange = initializationRange,
            mediaSegments = index.references.map { reference ->
                DashSegment(segmentBase.mediaUrl, reference.byteRange)
            },
            segmentBase = null
        )
    }

    /** Downloads one track's init and media pieces into files, then concatenates them in order. */
    private fun downloadTrack(
        context: Context,
        track: DashTrack,
        recoveryDir: File,
        label: String,
        itemOffset: Int,
        download: Download,
        completedCount: AtomicInteger,
        recoverySession: DownloadRecoverySession,
        verified: Set<Int>
    ): File? {
        if (track.mediaSegments.isEmpty()) return null
        val segmentFiles = List(track.mediaSegments.size) { index ->
            DownloadRecoveryStore.itemFile(context, download.id, itemOffset + index)
        }
        val missing = segmentFiles.indices.filterNot {
            itemOffset + it in verified
        }
        boundedSegmentWork(missing, segmentExecutor, checkActive = download::checkTransferActive) { index ->
            val temporary = File(segmentFiles[index].parentFile, "${segmentFiles[index].name}.tmp")
            try {
                temporary.delete()
                fetchToFile(track.mediaSegments[index], temporary, download)
                download.checkTransferActive()
                DownloadRecoveryStore.commitVerifiedItem(temporary, segmentFiles[index])
                completedCount.incrementAndGet()
                download.addProgress(1L)
                recoverySession.updateElapsed(download.elapsedActiveMillis())
            } finally { temporary.delete() }
        }
        val initFile = track.initSegmentUrl?.let { initUrl ->
            File(recoveryDir, "${label}_init.part").also { file ->
                if (!DownloadRecoveryStore.isVerifiedItem(file, verifyDigest = true)) {
                    val temporary = File(file.parentFile, "${file.name}.tmp")
                    temporary.delete()
                    fetchToFile(DashSegment(initUrl, track.initSegmentRange), temporary, download)
                    DownloadRecoveryStore.commitVerifiedItem(temporary, file)
                }
            }
        }
        val outputFile = File(recoveryDir, "$label.mp4")
        outputFile.outputStream().use { output ->
            initFile?.inputStream()?.use { it.copyPausableTo(output, download) }
            for (file in segmentFiles) {
                download.awaitIfPaused()
                if (download.isCanceled) return null
                file.inputStream().use { it.copyPausableTo(output, download) }
            }
        }
        return outputFile
    }

    private fun fetchExactRange(
        url: String,
        range: DashByteRange,
        maximumBytes: Long,
        download: Download,
        label: String
    ): ByteArray {
        if (range.length > maximumBytes || range.length > Int.MAX_VALUE) {
            throw DashDownloadException("$label range is too large")
        }
        val connection = openConnection(url, download, range)
        try {
            validateRangeResponse(connection, range, label)
            val expected = range.length.toInt()
            val result = ByteArray(expected)
            var offset = 0
            connection.inputStream.use { input ->
                while (offset < expected) {
                    download.awaitIfPaused()
                    if (download.isCanceled) throw DashDownloadException("Download canceled")
                    val read = input.read(result, offset, expected - offset)
                    if (read == -1) break
                    if (read > 0) offset += read
                }
            }
            if (offset != expected) throw DashDownloadException("$label range ended early")
            return result
        } finally {
            download.closeConnection(connection)
        }
    }

    private fun fetchToFile(segment: DashSegment, outputFile: File, download: Download) {
        val connection = openConnection(segment.url, download, segment.byteRange)
        try {
            if (segment.byteRange != null) {
                validateRangeResponse(connection, segment.byteRange, "DASH media")
            } else if (connection.responseCode !in 200..299) {
                throw DashDownloadException("Segment HTTP ${connection.responseCode}")
            }
            var written = 0L
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        download.awaitIfPaused()
                        if (download.isCanceled) throw DashDownloadException("Download canceled")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        segment.byteRange?.let { range ->
                            if (written > range.length) {
                                throw DashDownloadException("DASH range exceeded its declared length")
                            }
                        }
                    }
                }
            }
            segment.byteRange?.let { range ->
                if (written != range.length) throw DashDownloadException("DASH range ended early")
            }
        } finally {
            download.closeConnection(connection)
        }
    }

    private fun validateRangeResponse(connection: HttpURLConnection, range: DashByteRange, label: String) {
        if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw DashDownloadException("$label server did not honor byte ranges (HTTP ${connection.responseCode})")
        }
        val returned = parseDashContentRange(connection.getHeaderField("Content-Range"))
            ?: throw DashDownloadException("$label response is missing a valid Content-Range")
        if (returned.first != range.start || returned.second != range.endInclusive) {
            throw DashDownloadException("$label server returned the wrong byte range")
        }
    }

    private fun trackIdentity(label: String, track: DashTrack): List<String> = buildList {
        add("track:$label")
        add("init:${track.initSegmentUrl.orEmpty()}|${rangeIdentity(track.initSegmentRange)}")
        track.mediaSegments.forEachIndexed { index, segment ->
            add("$index:${segment.url}|${rangeIdentity(segment.byteRange)}")
        }
    }

    private fun rangeIdentity(range: DashByteRange?): String =
        range?.let { "${it.start}-${it.endInclusive}" }.orEmpty()

    private fun markCanceled(context: Context, download: Download) {
        if (!download.preserveRecoveryOnCancel) DownloadRecoveryStore.clear(context, download.id)
        download.status = DownloadStatus.CANCELED
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

    private fun openConnection(
        url: String,
        download: Download,
        range: DashByteRange? = null
    ): HttpURLConnection = download.openConnection(URL(url)).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", download.userAgent)
        setRequestProperty("Referer", download.referer)
        download.cookies?.let { setRequestProperty("Cookie", it) }
        range?.let { setRequestProperty("Range", "bytes=${it.start}-${it.endInclusive}") }
    }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }

    private class DashDownloadException(message: String) : Exception(message)
}

internal fun parseDashContentRange(value: String?): Pair<Long, Long>? {
    if (value == null) return null
    val match = Regex("^bytes\\s+(\\d+)-(\\d+)/(?:\\d+|\\*)$", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    return (start to end).takeIf { end >= start }
}
