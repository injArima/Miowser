package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import com.miowser.app.grabber.DashPlaylistParser
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

/**
 * DASH download pipeline: parse the MPD's full segment lists (DashPlaylistParser), pick the
 * best-bandwidth video and audio Representations, download each one's init segment + all media
 * segments concurrently, concatenate each track's own segments into one file per track (valid on
 * its own — concatenating a CMAF init segment with its media segments byte-for-byte produces a
 * playable fMP4, unlike HLS's raw .ts segments, so no MediaExtractor/MediaMuxer pass is needed
 * per track), then mux the (up to) two track files together into the final .mp4 via
 * SegmentMuxer.muxTracks — simpler than HLS's own mux step since each input file already holds
 * one complete, continuously-timestamped track; no cross-segment timestamp rebasing needed here.
 *
 * Scope, matching DashPlaylistParser: SegmentTemplate and SegmentList addressing only, not
 * SegmentBase's single-file sidx-indexed form.
 */
object DashDownloader {
    private val segmentExecutor = Executors.newFixedThreadPool(4)

    fun run(context: Context, download: Download) {
        val cacheDir = File(context.cacheDir, "dash_download_${download.id}")
        try {
            download.status = DownloadStatus.DOWNLOADING
            cacheDir.mkdirs()

            val body = fetchText(download.sourceUrl, download) ?: return fail(download, "Could not fetch manifest")
            val manifest = DashPlaylistParser.parse(body, download.sourceUrl)

            val videoTrack = manifest.tracks.filter { it.type == "video" }.maxByOrNull { it.bandwidth ?: 0L }
            val audioTrack = manifest.tracks.filter { it.type == "audio" }.maxByOrNull { it.bandwidth ?: 0L }
            if (videoTrack == null && audioTrack == null) return fail(download, "No downloadable video/audio track found in this MPD")

            download.totalBytes = ((videoTrack?.mediaSegments?.size ?: 0) + (audioTrack?.mediaSegments?.size ?: 0)).toLong()
            val completedCount = AtomicInteger(0)

            val videoFile = videoTrack?.let { downloadTrack(it, cacheDir, "video", download, completedCount) }
            if (download.isCanceled) return markCanceled(download)
            val audioFile = audioTrack?.let { downloadTrack(it, cacheDir, "audio", download, completedCount) }
            if (download.isCanceled) return markCanceled(download)

            if (videoFile == null && audioFile == null) return fail(download, "Segment download failed")

            download.status = DownloadStatus.MUXING
            val muxedFile = File(cacheDir, "muxed.mp4")
            try {
                SegmentMuxer.muxTracks(videoFile, audioFile, muxedFile.absolutePath)
            } catch (e: SegmentMuxer.MuxException) {
                return fail(download, e.message ?: "Muxing failed")
            }

            val opened = MediaStoreOutput.open(context, "${download.outputBaseName}.mp4", "video/mp4")
                ?: return fail(download, "Could not create output file")
            muxedFile.inputStream().use { input -> opened.stream.use { output -> input.copyTo(output) } }
            MediaStoreOutput.finish(context, opened.uri)

            download.status = DownloadStatus.COMPLETED
        } catch (e: Exception) {
            Log.d(TAG, "DASH download failed for ${download.sourceUrl}: ${e.message}")
            fail(download, e.message ?: "Unknown error")
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    /** Downloads one track's init segment (if any) + all media segments concurrently, concatenated in order into one file. Null on any failure. */
    private fun downloadTrack(track: DashTrack, cacheDir: File, label: String, download: Download, completedCount: AtomicInteger): File? {
        if (track.mediaSegments.isEmpty()) return null
        val segmentBytes = arrayOfNulls<ByteArray>(track.mediaSegments.size)
        val failure = AtomicReference<String?>(null)
        val latch = CountDownLatch(track.mediaSegments.size)

        for ((index, segment) in track.mediaSegments.withIndex()) {
            segmentExecutor.execute {
                try {
                    if (!download.isCanceled && failure.get() == null) {
                        segmentBytes[index] = fetchBytes(segment.url, download) ?: throw Exception("Segment HTTP failure")
                        download.bytesDownloaded = completedCount.incrementAndGet().toLong()
                    }
                } catch (e: Exception) {
                    failure.compareAndSet(null, e.message ?: "Segment download failed")
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        if (download.isCanceled || failure.get() != null) return null

        val outputFile = File(cacheDir, "$label.mp4")
        outputFile.outputStream().use { out ->
            track.initSegmentUrl?.let { url -> fetchBytes(url, download)?.let { out.write(it) } }
            for (bytes in segmentBytes) bytes?.let { out.write(it) }
        }
        return outputFile
    }

    private fun markCanceled(download: Download) {
        download.status = DownloadStatus.CANCELED
    }

    private fun fetchBytes(url: String, download: Download): ByteArray? {
        val connection = openConnection(url, download)
        return try {
            if (connection.responseCode !in 200..299) null else connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(url: String, download: Download): String? {
        val connection = openConnection(url, download)
        return try {
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, download: Download): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", download.userAgent)
            setRequestProperty("Referer", download.referer)
            download.cookies?.let { setRequestProperty("Cookie", it) }
        }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }
}
