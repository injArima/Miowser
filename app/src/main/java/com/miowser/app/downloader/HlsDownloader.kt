package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import com.miowser.app.grabber.HlsPlaylistParser
import com.miowser.app.grabber.HlsSegment
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

/**
 * The real HLS download pipeline: resolve a master playlist to its best variant (the same one
 * MediaProbe already picked for the grabber's own resolution display, so what downloads matches
 * what the user saw), fetch that leaf playlist's full segment list, download every segment
 * concurrently to a private cache dir, decrypt each one if AES-128-encrypted (HlsAes128Decryptor),
 * remux the set into a single .mp4 (SegmentMuxer), and finally copy that into the public
 * Downloads collection. Segments never touch the public output location themselves — only the
 * final muxed file does.
 */
object HlsDownloader {
    private val segmentExecutor = Executors.newFixedThreadPool(4)

    fun run(context: Context, download: Download) {
        val cacheDir = File(context.cacheDir, "hls_download_${download.id}")
        try {
            download.status = DownloadStatus.DOWNLOADING
            cacheDir.mkdirs()

            val requestedBody = fetchText(download.sourceUrl, download)
                ?: return fail(download, "Could not fetch playlist")

            // A master playlist has no segments of its own — follow its best variant (mirrors
            // ManifestSniffer's own resolution pick from the grab-time probe) to get to one.
            val sniffed = ManifestSniffer.sniff(requestedBody, download.sourceUrl)
            val (leafBody, leafUrl) = if (sniffed.isMasterOrMultiVariant) {
                val variantUrl = sniffed.bestVariantUrl ?: return fail(download, "Master playlist has no usable variant")
                val variantBody = fetchText(variantUrl, download) ?: return fail(download, "Could not fetch variant playlist")
                variantBody to variantUrl
            } else {
                requestedBody to download.sourceUrl
            }

            val playlist = HlsPlaylistParser.parseLeaf(leafBody, leafUrl)
            if (playlist.segments.isEmpty()) return fail(download, "Playlist has no segments")

            val segmentFiles = downloadAllSegments(playlist.segments, cacheDir, download) ?: return
            if (download.isCanceled) {
                download.status = DownloadStatus.CANCELED
                return
            }

            download.status = DownloadStatus.MUXING
            val muxedFile = File(cacheDir, "muxed.mp4")
            try {
                SegmentMuxer.mux(segmentFiles, muxedFile.absolutePath)
            } catch (e: SegmentMuxer.MuxException) {
                return fail(download, e.message ?: "Muxing failed")
            }

            val opened = MediaStoreOutput.open(context, "${download.outputBaseName}.mp4", "video/mp4")
                ?: return fail(download, "Could not create output file")
            muxedFile.inputStream().use { input -> opened.stream.use { output -> input.copyTo(output) } }
            MediaStoreOutput.finish(context, opened.uri, "video/mp4")

            download.status = DownloadStatus.COMPLETED
        } catch (e: Exception) {
            Log.d(TAG, "HLS download failed for ${download.sourceUrl}: ${e.message}")
            fail(download, e.message ?: "Unknown error")
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    /** Downloads every segment concurrently on a shared 4-thread pool; returns null (having already failed the download) on any error. */
    private fun downloadAllSegments(segments: List<HlsSegment>, cacheDir: File, download: Download): List<File>? {
        val segmentFiles = arrayOfNulls<File>(segments.size)
        val downloadedCount = AtomicInteger(0)
        val failure = AtomicReference<String?>(null)
        val latch = CountDownLatch(segments.size)
        download.totalBytes = segments.size.toLong() // progress unit is "segments" here, not bytes — see Download's doc comment

        for ((index, segment) in segments.withIndex()) {
            segmentExecutor.execute {
                try {
                    if (!download.isCanceled && failure.get() == null) {
                        val file = File(cacheDir, "seg_$index.ts")
                        downloadSegment(segment, file, download)
                        segmentFiles[index] = file
                        download.bytesDownloaded = downloadedCount.incrementAndGet().toLong()
                    }
                } catch (e: Exception) {
                    failure.compareAndSet(null, e.message ?: "Segment download failed")
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        failure.get()?.let {
            fail(download, it)
            return null
        }
        return segmentFiles.filterNotNull()
    }

    private fun downloadSegment(segment: HlsSegment, outputFile: File, download: Download) {
        val connection = openConnection(segment.url, download)
        try {
            if (connection.responseCode !in 200..299) throw Exception("Segment HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytes() }
            val finalBytes = if (segment.keyUri != null) {
                val key = fetchBytes(segment.keyUri, download) ?: throw Exception("Could not fetch decryption key")
                HlsAes128Decryptor.decrypt(bytes, key, segment.ivHex, segment.sequenceNumber)
            } else {
                bytes
            }
            outputFile.writeBytes(finalBytes)
        } finally {
            connection.disconnect()
        }
    }

    // Keys are tiny and shared across many segments; a real cache would avoid re-fetching the
    // same key per segment, but with only 4 segment threads and almost always a single key
    // covering the whole playlist, the duplicate fetches this causes are cheap enough not to be
    // worth the extra synchronization for a first version.
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
