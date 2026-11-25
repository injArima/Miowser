package com.miowser.app.downloader

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

internal const val DOWNLOAD_SPACE_RESERVE = 32L * 1024 * 1024

internal fun requiredDownloadHeadroom(remainingBytes: Long, reservedBytes: Long): Long =
    remainingBytes.coerceAtLeast(0).let { remaining ->
        if (reservedBytes > Long.MAX_VALUE - DOWNLOAD_SPACE_RESERVE ||
            remaining > Long.MAX_VALUE - reservedBytes.coerceAtLeast(0) - DOWNLOAD_SPACE_RESERVE) Long.MAX_VALUE
        else remaining + reservedBytes.coerceAtLeast(0) + DOWNLOAD_SPACE_RESERVE
    }

/** Reservations cover concurrent jobs; real free-space checks also run during writes. */
object DownloadStorage {
    private data class Reservation(val device: Long, val directory: File, val remaining: Long)
    private val reservations = mutableMapOf<Long, Reservation>()
    private val lastChecks = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    @Synchronized
    fun reserve(context: Context, download: Download, bytes: Long) {
        val directory = DownloadRecoveryStore.directory(context, download.id).also { it.mkdirs() }
        if (!directory.isDirectory || !directory.canWrite()) throw TransferException(TransferFailure(
            TransferFailureKind.STORAGE, "Reconnect the storage containing this download's temporary files, then resume."))
        val device = android.system.Os.stat(directory.absolutePath).st_dev
        val others = reservations.filterKeys { it != download.id }.values.filter { it.device == device }.sumOf { it.remaining }
        val required = requiredDownloadHeadroom(bytes, others)
        if (StatFs(directory.absolutePath).availableBytes < required) insufficient("Temporary download storage", required)
        reservations[download.id] = Reservation(device, directory, bytes.coerceAtLeast(0))
    }

    @Synchronized
    fun wrote(download: Download, count: Long) {
        reservations[download.id]?.let { reservations[download.id] = it.copy(remaining = (it.remaining - count).coerceAtLeast(0)) }
        val now = System.nanoTime()
        if (now - (lastChecks[download.id] ?: 0L) < 1_000_000_000L) return
        lastChecks[download.id] = now
        reservations[download.id]?.let {
            if (StatFs(it.directory.absolutePath).availableBytes < DOWNLOAD_SPACE_RESERVE) insufficient("Temporary download storage", DOWNLOAD_SPACE_RESERVE)
        }
    }

    @Synchronized fun release(id: Long) { reservations.remove(id); lastChecks.remove(id) }

    fun checkPublication(context: Context, source: File) {
        checkDestination(context, source.length())
    }

    fun checkDestination(context: Context, bytes: Long) {
        val directory = destinationDirectory(context)
        // A remote document provider may not expose free space. Its write errors still surface.
        if (directory != null && directory.exists()) {
            val required = requiredDownloadHeadroom(bytes, 0)
            if (StatFs(directory.absolutePath).availableBytes < required) insufficient("Download destination", required)
        }
    }

    /** Extra copy space on the staging volume. Emulated primary storage shares /data capacity. */
    fun publicationBytes(context: Context, downloadId: Long, bytes: Long): Long {
        val destination = destinationDirectory(context) ?: return bytes.coerceAtLeast(0)
        val staging = DownloadRecoveryStore.directory(context, downloadId)
        val manager = context.getSystemService(StorageManager::class.java)
        val targetVolume = manager?.getStorageVolume(destination)
        val sourceVolume = manager?.getStorageVolume(staging)
        val sharedPrimary = targetVolume?.isPrimary == true && targetVolume.isEmulated &&
            (staging.absolutePath.startsWith(context.filesDir.absolutePath + File.separator) || sourceVolume?.isPrimary == true)
        val sameDevice = runCatching { android.system.Os.stat(staging.absolutePath).st_dev == android.system.Os.stat(destination.absolutePath).st_dev }.getOrDefault(true)
        return if (sharedPrimary || sameDevice) bytes.coerceAtLeast(0) else 0
    }

    private fun destinationDirectory(context: Context): File? {
        val destination = DownloadDirectoryStore.load(context)
        val storage = context.getSystemService(StorageManager::class.java)
        val volume = if (destination?.treeUri != null) {
            val volumeId = runCatching { DocumentsContract.getTreeDocumentId(destination.treeUri).substringBefore(':') }.getOrNull()
            storage?.storageVolumes?.firstOrNull { if (volumeId == "primary") it.isPrimary else it.uuid.equals(volumeId, true) }
        } else storage?.storageVolumes?.firstOrNull { it.isPrimary }
        return if (android.os.Build.VERSION.SDK_INT >= 30) volume?.directory else null
    }

    private fun insufficient(location: String, bytes: Long): Nothing = throw TransferException(
        TransferFailure(TransferFailureKind.STORAGE, "$location needs more free space (at least ${(bytes / (1024 * 1024)).coerceAtLeast(1)} MB). Free space and resume this download."))
}
