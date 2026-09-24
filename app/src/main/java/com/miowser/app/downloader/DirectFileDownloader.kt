package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import com.miowser.app.settings.AppSettings
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "Miowser/Downloader"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_UPDATE_INTERVAL_BYTES = 256 * 1024L
private const val SIGNATURE_PROBE_BYTES = 64 * 1024
private const val MIN_BYTES_PER_THREAD = 1L * 1024 * 1024

/** Direct transfer with durable ranged recovery and a safe streaming fallback. */
object DirectFileDownloader {
    fun run(context: Context, download: Download) {
        download.enterStage(DownloadStatus.DOWNLOADING)
        download.awaitIfPaused()
        if (download.isCanceled) return cancel(context, download)
        download.threadProgress.clear()

        val probe = try {
            openConnection(download, range = "bytes=0-")
        } catch (e: Exception) {
            return fail(download, "Connection failed: ${e.message}")
        }
        try {
            when (probe.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> runRecoverableRanges(context, download, probe)
                in 200..299 -> runFreshStream(context, download, probe)
                else -> fail(download, "HTTP ${probe.responseCode}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "download failed for ${download.sourceUrl}: ${e.message}")
            if (download.isCanceled) cancel(context, download) else fail(download, e.message ?: "Unknown error")
        } finally {
            probe.disconnect()
        }
    }

    private fun runRecoverableRanges(context: Context, download: Download, probe: HttpURLConnection) {
        val total = totalFromContentRange(probe.getHeaderField("Content-Range"))
            ?: throw Exception("Ranged response did not include a total size")
        val contentType = probe.getHeaderField("Content-Type")
        val signature = BufferedInputStream(probe.inputStream).use(::readPrefix)
        val identity = resolveIdentity(download, probe, contentType, signature)
        val fingerprint = sha256(signature)
        val etag = probe.getHeaderField("ETag")
        val lastModified = probe.getHeaderField("Last-Modified")
        probe.disconnect()

        val existing = DownloadRecoveryStore.load(context, download.id)
        val payload = DownloadRecoveryStore.payloadFile(context, download.id)
        val reusable = existing?.takeIf { plan ->
            plan.transferKind == RecoveryTransferKind.DIRECT_RANGES &&
                plan.totalLength == total && plan.sourceFingerprint == fingerprint &&
                validatorsCompatible(plan.etag, etag, plan.lastModified, lastModified) &&
                plan.outputFileName == identity.fileName && payload.isFile && plan.ranges.isNotEmpty()
        }
        if (existing != null && reusable == null) DownloadRecoveryStore.clear(context, download.id)

        val ranges = reusable?.ranges ?: splitIntoRanges(
            total,
            AppSettings.current.downloadThreadCount.count
                .takeIf { it > 1 && total >= MIN_BYTES_PER_THREAD * 2 } ?: 1
        ).map { (start, end) -> RecoveryRange(start, end, 0L) }
        val plan = reusable ?: DownloadRecoveryPlan(
            downloadId = download.id,
            transferKind = RecoveryTransferKind.DIRECT_RANGES,
            sourceFingerprint = fingerprint,
            etag = etag,
            lastModified = lastModified,
            totalLength = total,
            outputFileName = identity.fileName,
            mimeType = identity.mimeType,
            itemFingerprint = null,
            itemCount = ranges.size,
            ranges = ranges
        )
        val session = DownloadRecoverySession(context, plan)
        download.displayName = identity.fileName
        download.totalBytes = total
        ranges.forEach { range ->
            DownloadThreadProgress(range.start, range.endInclusive).also {
                it.downloadedBytes = range.downloadedBytes
                download.threadProgress.add(it)
            }
        }
        download.bytesDownloaded = ranges.sumOf(RecoveryRange::downloadedBytes)

        payload.parentFile?.mkdirs()
        RandomAccessFile(payload, "rw").use { output ->
            output.setLength(total)
            when (downloadRanges(download, output.channel, session)) {
                RangeResult.NOT_HONORED -> {
                    output.setLength(0L)
                    DownloadRecoveryStore.clear(context, download.id)
                    download.threadProgress.clear()
                    download.bytesDownloaded = 0L
                    val fallback = openConnection(download)
                    try {
                        runFreshStream(context, download, fallback)
                    } finally {
                        fallback.disconnect()
                    }
                    return
                }
                RangeResult.CANCELED -> return cancel(context, download)
                RangeResult.FAILED -> throw Exception("One or more byte ranges failed")
                RangeResult.COMPLETED -> output.channel.force(true)
            }
        }
        publishCompleted(context, download, payload, identity.fileName, identity.mimeType)
    }

    private fun downloadRanges(download: Download, channel: FileChannel, session: DownloadRecoverySession): RangeResult {
        val plan = session.snapshot()
        val pending = plan.ranges.indices.filter { index ->
            val range = plan.ranges[index]
            range.downloadedBytes < range.endInclusive - range.start + 1L
        }
        if (pending.isEmpty()) return RangeResult.COMPLETED
        val executor = Executors.newFixedThreadPool(pending.size.coerceAtMost(plan.ranges.size))
        val latch = CountDownLatch(pending.size)
        val failure = AtomicReference<Throwable?>(null)
        pending.forEach { index ->
            executor.execute {
                try {
                    if (failure.get() == null && !download.isCanceled) downloadRange(download, channel, session, index)
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()
        if (download.isCanceled) return RangeResult.CANCELED
        return when (failure.get()) {
            null -> RangeResult.COMPLETED
            is RangeNotHonoredException -> RangeResult.NOT_HONORED
            else -> RangeResult.FAILED
        }
    }

    private fun downloadRange(download: Download, channel: FileChannel, session: DownloadRecoverySession, index: Int) {
        val plan = session.snapshot()
        val saved = plan.ranges[index]
        val rangeLength = saved.endInclusive - saved.start + 1L
        var completed = saved.downloadedBytes.coerceIn(0L, rangeLength)
        if (completed == rangeLength) return
        val requestStart = saved.start + completed
        val connection = openConnection(
            download,
            range = "bytes=$requestStart-${saved.endInclusive}",
            ifRange = plan.etag ?: plan.lastModified
        )
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw RangeNotHonoredException("Server no longer honored the saved byte range")
            }
            val returned = parseContentRange(connection.getHeaderField("Content-Range"))
                ?: throw RangeNotHonoredException("Ranged response was missing Content-Range")
            if (returned.first != requestStart || returned.second != saved.endInclusive || returned.third != plan.totalLength) {
                throw RangeNotHonoredException("Source changed while resuming")
            }
            var position = requestStart
            var lastPersisted = completed
            BufferedInputStream(connection.inputStream).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (completed < rangeLength) {
                    download.awaitIfPaused()
                    if (download.isCanceled) return
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), rangeLength - completed).toInt())
                    if (read == -1) break
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    while (byteBuffer.hasRemaining()) {
                        val written = channel.write(byteBuffer, position)
                        if (written <= 0) throw Exception("Could not write byte range to recovery file")
                        position += written
                    }
                    completed += read
                    download.threadProgress[index].downloadedBytes = completed
                    download.bytesDownloaded = download.threadProgress.sumOf { it.downloadedBytes }
                    if (completed - lastPersisted >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                        channel.force(false)
                        session.updateRange(index, completed, download.elapsedActiveMillis())
                        lastPersisted = completed
                    }
                }
            }
            if (completed != rangeLength) throw Exception("Byte range ended early")
            channel.force(false)
            session.updateRange(index, completed, download.elapsedActiveMillis())
        } finally {
            connection.disconnect()
        }
    }

    private fun runFreshStream(context: Context, download: Download, connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) return fail(download, "HTTP ${connection.responseCode}")
        val total = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
        val input = BufferedInputStream(connection.inputStream)
        val signature = peekPrefix(input)
        val identity = resolveIdentity(download, connection, connection.getHeaderField("Content-Type"), signature)
        DownloadRecoveryStore.clear(context, download.id)
        val recoverySession = DownloadRecoverySession(
            context,
            DownloadRecoveryPlan(
                downloadId = download.id,
                transferKind = RecoveryTransferKind.DIRECT_STREAM,
                sourceFingerprint = sha256(signature),
                etag = connection.getHeaderField("ETag"),
                lastModified = connection.getHeaderField("Last-Modified"),
                totalLength = total,
                outputFileName = identity.fileName,
                mimeType = identity.mimeType,
                itemFingerprint = null,
                itemCount = 1
            )
        )
        val payload = DownloadRecoveryStore.payloadFile(context, download.id)
        payload.parentFile?.mkdirs()
        download.displayName = identity.fileName
        download.totalBytes = total
        download.bytesDownloaded = 0L
        payload.outputStream().buffered().use { output ->
            input.use { source ->
                val buffer = ByteArray(BUFFER_SIZE)
                var written = 0L
                var lastPersisted = 0L
                while (true) {
                    download.awaitIfPaused()
                    if (download.isCanceled) return cancel(context, download)
                    val read = source.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    download.bytesDownloaded = written
                    if (written - lastPersisted >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                        output.flush()
                        recoverySession.updateElapsed(download.elapsedActiveMillis())
                        lastPersisted = written
                    }
                }
            }
        }
        publishCompleted(context, download, payload, identity.fileName, identity.mimeType)
    }

    private fun publishCompleted(context: Context, download: Download, payload: File, fileName: String, mimeType: String) {
        val uri = MediaStoreOutput.publishFile(context, payload, fileName, mimeType)
            ?: return fail(download, "Could not create output file")
        MediaStoreOutput.legacyFileName(uri)?.let { download.displayName = it }
        download.savedUri = uri
        download.bytesDownloaded = download.totalBytes.takeIf { it >= 0L } ?: payload.length()
        download.status = DownloadStatus.COMPLETED
        DownloadRecoveryStore.clear(context, download.id)
    }

    private fun openConnection(download: Download, range: String? = null, ifRange: String? = null): HttpURLConnection =
        (URL(download.sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", download.userAgent)
            setRequestProperty("Referer", download.referer)
            download.cookies?.let { setRequestProperty("Cookie", it) }
            range?.let { setRequestProperty("Range", it) }
            ifRange?.let { setRequestProperty("If-Range", it) }
        }

    private fun splitIntoRanges(totalBytes: Long, threadCount: Int): List<Pair<Long, Long>> {
        val count = threadCount.coerceAtLeast(1).coerceAtMost(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val chunkSize = totalBytes / count
        return (0 until count).map { index ->
            val start = index * chunkSize
            val end = if (index == count - 1) totalBytes - 1 else start + chunkSize - 1
            start to end
        }
    }

    private fun validatorsCompatible(oldEtag: String?, newEtag: String?, oldModified: String?, newModified: String?): Boolean {
        if (oldEtag != null || newEtag != null) return oldEtag != null && oldEtag == newEtag
        if (oldModified != null || newModified != null) return oldModified != null && oldModified == newModified
        return true
    }

    private fun totalFromContentRange(value: String?): Long? = parseContentRange(value)?.third

    private fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
        val match = value?.trim()?.let {
            Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(it)
        } ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return Triple(start, end, total).takeIf { start <= end && end < total }
    }

    private fun resolveIdentity(download: Download, connection: HttpURLConnection, contentType: String?, signature: ByteArray) =
        DownloadIdentityResolver.resolve(
            DownloadIdentityInput(
                originalUrl = download.sourceUrl,
                finalUrl = connection.url.toString(),
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                declaredMimeType = contentType,
                browserSuggestedName = download.displayName,
                fallbackBaseName = download.outputBaseName,
                preferredBaseName = download.preferredOutputBaseName,
                signature = signature
            )
        )

    private fun readPrefix(input: BufferedInputStream): ByteArray {
        val bytes = ByteArray(SIGNATURE_PROBE_BYTES)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read == -1) break
            count += read
        }
        return bytes.copyOf(count)
    }

    private fun peekPrefix(input: BufferedInputStream): ByteArray {
        input.mark(SIGNATURE_PROBE_BYTES + 1)
        val prefix = readPrefix(input)
        input.reset()
        return prefix
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun cancel(context: Context, download: Download) {
        DownloadRecoveryStore.clear(context, download.id)
        download.status = DownloadStatus.CANCELED
    }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }

    private enum class RangeResult { COMPLETED, CANCELED, NOT_HONORED, FAILED }
    private class RangeNotHonoredException(message: String) : Exception(message)
}

internal fun sniffDownloadedFileExtension(bytes: ByteArray): String? =
    DownloadIdentityResolver.sniffExtension(bytes)
