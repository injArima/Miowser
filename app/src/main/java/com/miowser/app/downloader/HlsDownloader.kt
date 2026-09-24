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
            if (playlist.segments.isEmpty()) return fail(download, "Playlist has no segments")
            val finalFileName = "${download.outputBaseName}.mp4"
            val fingerprint = recoveryFingerprint(playlist.segments.map(::segmentIdentity))
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

            download.enterStage(DownloadStatus.MUXING)
            download.awaitIfPaused()
            if (download.isCanceled) return cancel(context, download)
            val muxedFile = File(recoveryDir, "muxed.mp4")
            try {
                SegmentMuxer.mux(segmentFiles, muxedFile.absolutePath)
            } catch (e: SegmentMuxer.MuxException) {
                return fail(download, e.message ?: "Muxing failed")
            }

            download.displayName = finalFileName
            val uri = MediaStoreOutput.publishFile(context, muxedFile, finalFileName, "video/mp4")
                ?: return fail(download, "Could not create output file")
            MediaStoreOutput.legacyFileName(uri)?.let { download.displayName = it }
            download.savedUri = uri
            download.status = DownloadStatus.COMPLETED
            DownloadRecoveryStore.clear(context, download.id)
        } catch (e: Exception) {
            Log.d(TAG, "HLS download failed for ${download.sourceUrl}: ${e.message}")
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
        val completed = AtomicInteger(files.count { DownloadRecoveryStore.isVerifiedItem(it, verifyDigest = true) })
        download.totalBytes = segments.size.toLong()
        download.bytesDownloaded = completed.get().toLong()
        val missing = files.indices.filterNot { DownloadRecoveryStore.isVerifiedItem(files[it], verifyDigest = true) }
        if (missing.isEmpty()) return files

        val failure = AtomicReference<String?>(null)
        val latch = CountDownLatch(missing.size)
        missing.forEach { index ->
            segmentExecutor.execute {
                val target = files[index]
                val temporary = File(target.parentFile, "${target.name}.tmp")
                try {
                    download.awaitIfPaused()
                    if (!download.isCanceled && failure.get() == null) {
                        temporary.delete()
                        downloadSegment(segments[index], temporary, download)
                        if (!download.isCanceled) {
                            DownloadRecoveryStore.commitVerifiedItem(temporary, target)
                            download.bytesDownloaded = completed.incrementAndGet().toLong()
                            recoverySession.updateElapsed(download.elapsedActiveMillis())
                        }
                    }
                } catch (e: Exception) {
                    failure.compareAndSet(null, e.message ?: "Segment download failed")
                } finally {
                    temporary.delete()
                    latch.countDown()
                }
            }
        }
        latch.await()
        if (download.isCanceled) return null
        failure.get()?.let {
            fail(download, it)
            return null
        }
        return files.takeIf { it.all { file -> DownloadRecoveryStore.isVerifiedItem(file, verifyDigest = true) } }
            ?: run { fail(download, "A downloaded segment could not be verified"); null }
    }

    private fun downloadSegment(segment: HlsSegment, outputFile: File, download: Download) {
        val connection = openConnection(segment.url, download)
        try {
            if (connection.responseCode !in 200..299) throw Exception("Segment HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytesPausable(download) }
            val finalBytes = if (segment.keyUri != null) {
                val key = fetchBytes(segment.keyUri, download) ?: throw Exception("Could not fetch decryption key")
                HlsAes128Decryptor.decrypt(bytes, key, segment.ivHex, segment.sequenceNumber)
            } else bytes
            outputFile.writeBytes(finalBytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun segmentIdentity(segment: HlsSegment): String = listOf(
        segment.url,
        segment.keyUri.orEmpty(),
        segment.ivHex.orEmpty(),
        segment.sequenceNumber.toString()
    ).joinToString("|")

    private fun fetchBytes(url: String, download: Download): ByteArray? {
        val connection = openConnection(url, download)
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytesPausable(download) }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(url: String, download: Download): String? {
        val connection = openConnection(url, download)
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytesPausable(download).toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, download: Download): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", download.userAgent)
            setRequestProperty("Referer", download.referer)
            download.cookies?.let { setRequestProperty("Cookie", it) }
        }

    private fun cancel(context: Context, download: Download) {
        DownloadRecoveryStore.clear(context, download.id)
        download.status = DownloadStatus.CANCELED
    }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }
}
