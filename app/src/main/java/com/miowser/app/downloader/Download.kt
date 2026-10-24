package com.miowser.app.downloader

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, MUXING, COMPLETED, FAILED, CANCELED }

enum class DownloadKind { DIRECT_FILE, HLS, DASH, WEBVIEW_RESOURCE }

/**
 * One thread's own byte range within a multi-threaded direct-file download, and how much of it
 * it's done — see DirectFileDownloader. [startByte]/[endByte] are fixed for the thread's whole
 * life (inclusive, HTTP Range semantics); [downloadedBytes] is the only field that moves,
 * updated straight from that thread as it streams its chunk. Purely a UI/progress concern — the
 * download either finishes with every thread's range filled in or fails outright, so there's no
 * separate per-thread status to track.
 */
class DownloadThreadProgress(val startByte: Long, val endByte: Long) {
    private val exact = AtomicLong()
    private var visible by mutableLongStateOf(0L)
    var downloadedBytes: Long
        get() = exact.get()
        set(value) { exact.set(value) }
    val publishedBytes: Long get() = visible
    internal fun publish() { visible = exact.get() }
}

/**
 * One user-visible download. Progress fields are Compose-observable properties on a plain class
 * (like Tab), not a data class replaced wholesale in a list on every update — a download reports
 * progress many times a second, and re-inserting into a mutableStateListOf that often would
 * fight Compose's diffing for no benefit.
 *
 * [bytesDownloaded]/[totalBytes] mean actual bytes for a direct-file download, but *segments
 * completed/total* for HLS and DASH — neither's real byte total is known upfront (see
 * MediaProbe's own HLS size estimate, which is a separate, approximate number). Either way the
 * ratio is a valid 0..1 progress fraction, which is all the UI actually needs.
 */
class Download(
    val id: Long,
    val sourceUrl: String,
    val pageUrl: String,
    val userAgent: String,
    val referer: String,
    @Volatile var cookies: String?,
    val kind: DownloadKind,
    displayName: String,
    val outputBaseName: String,
    /** Extension from a webpage's Content-Disposition filename; null for grabber downloads. */
    val suggestedExtension: String? = null,
    /** Non-null only when the user explicitly chose "New renamed copy" after a conflict. */
    val preferredOutputBaseName: String? = null
) {
    /** Updated once the real response identifies a better filename than WebView's suggestion. */
    var displayName by mutableStateOf(displayName)

    private var observableStatus by mutableStateOf(DownloadStatus.QUEUED)
    var status: DownloadStatus
        get() = observableStatus
        set(value) {
            updateElapsedForStatusChange(observableStatus, value)
            publishProgress(force = true)
            observableStatus = value
        }
    private var observableBytesDownloaded by mutableLongStateOf(0L)
    var bytesDownloaded: Long
        get() = observableBytesDownloaded
        set(value) {
            exactBytes.set(value)
            publishProgress()
        }
    private val exactBytes = AtomicLong()
    private val cadence = ProgressCadence()
    @Synchronized fun publishProgress(force: Boolean = false) {
        val now = System.nanoTime()
        if (!cadence.shouldPublish(now, force)) return
        val bytes = exactBytes.get()
        updateProgressRate(bytes)
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            observableBytesDownloaded = bytes
            threadProgress.forEach { it.publish() }
        }
    }
    internal fun addProgress(bytes: Long) { exactBytes.addAndGet(bytes); publishProgress() }
    var totalBytes by mutableLongStateOf(-1L) // -1 = unknown
    var errorMessage by mutableStateOf<String?>(null)
    /** Transient connection errors recovered automatically during this attempt. */
    private val connectionRetryCounter = AtomicInteger(0)
    var retryCount by mutableIntStateOf(0)
        private set

    fun recordConnectionRetry() { retryCount = connectionRetryCounter.incrementAndGet() }
    fun resetConnectionRetries() { connectionRetryCounter.set(0); retryCount = 0 }

    // The finished file's real content:// (or, pre-Q, file://) Uri — set once, right alongside
    // MediaStoreOutput.finish() in each of the three downloaders, never touched again after that.
    // Null for anything not COMPLETED (queued/downloading/failed/canceled never had a finished
    // file to point at). This is what lets the Hub actually play a completed download locally
    // instead of only ever re-fetching from the original remote URL.
    var savedUri by mutableStateOf<Uri?>(null)
    /** Completion is not acknowledged until its presentation record reaches disk. */
    var recordSaved by mutableStateOf(false)
    var recoveryChecking by mutableStateOf(false)
    internal var startupRecord: SavedDownload? = null

    // Empty for anything that isn't a multi-threaded direct-file download in progress — single-
    // threaded downloads (HLS/DASH always; a direct file whose server didn't support ranged
    // requests, or where Settings.downloadThreadCount is 1) never populate this at all, which is
    // exactly how the UI tells the two apart: HubPage falls back to the plain aggregate bar when
    // this is empty, and shows a divided-into-chunks one when it isn't.
    val threadProgress = mutableStateListOf<DownloadThreadProgress>()

    /**
     * Process-local handoff for blob:/data: content. These URLs cannot be fetched by a normal
     * network worker; the originating Tab attaches a renderer-backed transfer before enqueue.
     * Deliberately not persisted: blob URLs die with their document, while storing a data URL
     * would duplicate its entire payload (and potentially private page data) in SharedPreferences.
     */
    @Volatile var rendererTransfer: ((Download) -> Unit)? = null
    @Volatile var rendererSourceUnavailable = false
        private set

    /** A dead page cannot reconnect. Wake paused work without disguising failure as Cancel. */
    fun invalidateRendererSource(message: String) = synchronized(pauseMonitor) {
        rendererSourceUnavailable = true
        rendererTransfer = null // release the closure's originating WebView/tab references
        if (status != DownloadStatus.COMPLETED && !isCanceled) {
            isPaused = false
            errorMessage = message
            status = DownloadStatus.FAILED
        }
        pauseMonitor.notifyAll()
    }

    // Checked from the download thread to stop early; not Compose state, and deliberately not
    // read by the UI (status transitioning to CANCELED is what the UI reacts to).
    @Volatile var isCanceled = false
    /** A link refresh stops this worker without deleting its verified recovery artifacts. */
    @Volatile var preserveRecoveryOnCancel = false
    @Volatile private var isPaused = false
    @Volatile internal var yieldRequested = false
        private set
    private val connections = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.HttpURLConnection>()
    internal fun beginAttempt() { yieldRequested = false }
    internal fun openConnection(url: java.net.URL): java.net.HttpURLConnection =
        com.miowser.app.network.NativeConnections.open(url,
            com.miowser.app.network.ConnectionBudget.Priority.TRANSFER, ::checkTransferActive).also {
                connections.add(it)
                if (yieldRequested || isCanceled) { closeConnection(it); checkTransferActive() }
            }
    internal fun closeConnection(connection: java.net.HttpURLConnection) {
        try { com.miowser.app.network.NativeConnections.close(connection) } finally { connections.remove(connection) }
    }
    private fun interruptConnections() {
        val retired = connections.toList()
        if (retired.isNotEmpty()) connectionCleanup.execute {
            retired.forEach { runCatching { it.disconnect() } }
        }
    }
    internal fun waitBeforeRetry(milliseconds: Long) {
        var left = milliseconds
        while (left > 0) {
            checkTransferActive()
            val slice = minOf(left, 100L)
            Thread.sleep(slice)
            left -= slice
        }
    }
    internal fun checkTransferActive() {
        awaitIfPaused()
        if (isCanceled) throw java.util.concurrent.CancellationException("Download canceled")
    }
    @Volatile private var statusBeforePause = DownloadStatus.DOWNLOADING
    private val pauseMonitor = Object()
    private val progressMonitor = Object()
    private val elapsedMonitor = Object()
    private var lastProgressValue = 0L
    private var lastProgressNanos = System.nanoTime()
    private var smoothedUnitsPerSecond = 0.0
    private var accumulatedActiveNanos = 0L
    private var activeSinceNanos: Long? = null

    /** Active transfer/muxing time only; time spent paused or waiting in the queue is excluded. */
    fun elapsedActiveMillis(): Long = synchronized(elapsedMonitor) {
        val running = activeSinceNanos?.let { System.nanoTime() - it } ?: 0L
        (accumulatedActiveNanos + running).coerceAtLeast(0L) / 1_000_000L
    }

    fun restoreElapsedTime(elapsedMillis: Long) = synchronized(elapsedMonitor) {
        accumulatedActiveNanos = elapsedMillis.coerceAtLeast(0L) * 1_000_000L
        activeSinceNanos = if (status.isTimedStage()) System.nanoTime() else null
    }

    fun resetElapsedTime() = synchronized(elapsedMonitor) {
        accumulatedActiveNanos = 0L
        activeSinceNanos = if (status.isTimedStage()) System.nanoTime() else null
    }

    fun requestPause(): Boolean = synchronized(pauseMonitor) {
        if (status != DownloadStatus.QUEUED && status != DownloadStatus.DOWNLOADING) return@synchronized false
        statusBeforePause = status
        isPaused = true
        yieldRequested = true
        interruptConnections()
        status = DownloadStatus.PAUSED
        resetProgressClock(keepRate = true)
        true
    }

    internal fun requestServiceCheckpoint(owned: Boolean = false) = synchronized(pauseMonitor) {
        if (status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.MUXING) ||
            (owned && status == DownloadStatus.FAILED)) {
            statusBeforePause = DownloadStatus.DOWNLOADING
            isPaused = true
            yieldRequested = true
            interruptConnections()
            status = DownloadStatus.PAUSED
        }
    }

    fun requestResume(): Boolean = synchronized(pauseMonitor) {
        if (status != DownloadStatus.PAUSED) return@synchronized false
        isPaused = false
        status = statusBeforePause
        resetProgressClock(keepRate = true)
        pauseMonitor.notifyAll()
        true
    }

    /** Restored paused work has no live worker; Queue starts a fresh attempt when resumed. */
    fun restorePausedState() = synchronized(pauseMonitor) {
        isPaused = true
        statusBeforePause = DownloadStatus.DOWNLOADING
        status = DownloadStatus.PAUSED
    }

    fun requestCancel() = synchronized(pauseMonitor) {
        isCanceled = true
        interruptConnections()
        isPaused = false
        pauseMonitor.notifyAll()
    }

    fun enterStage(stage: DownloadStatus) = synchronized(pauseMonitor) {
        statusBeforePause = stage
        if (!isPaused) status = stage
    }

    /** Releases the downloader's ownership at its next cooperative checkpoint. */
    fun awaitIfPaused() {
        if ((isPaused || yieldRequested) && !isCanceled) throw DownloadYieldException()
    }

    /**
     * Estimates time remaining from a smoothed rate of recent progress updates. For direct files
     * the units are bytes; for HLS/DASH they are completed segments. The ratio is what matters,
     * so one estimator correctly serves all three download kinds.
     */
    fun estimatedSecondsRemaining(): Long? = synchronized(progressMonitor) {
        val total = totalBytes
        val completed = observableBytesDownloaded
        if (total <= 0L || completed < 0L || completed >= total || smoothedUnitsPerSecond <= 0.0) {
            return@synchronized null
        }
        val idleSeconds = (System.nanoTime() - lastProgressNanos) / 1_000_000_000.0
        // A paused transfer is intentionally idle: retain its last rate so the frozen remaining
        // estimate stays visible. For a running transfer, stale progress still means the estimate
        // is no longer trustworthy and should disappear after 30 seconds.
        if (idleSeconds > 30.0 && status != DownloadStatus.PAUSED) return@synchronized null
        kotlin.math.ceil((total - completed) / smoothedUnitsPerSecond).toLong().coerceAtLeast(1L)
    }

    private fun updateProgressRate(newValue: Long) = synchronized(progressMonitor) {
        val now = System.nanoTime()
        if (newValue <= 0L || newValue < lastProgressValue) {
            lastProgressValue = newValue.coerceAtLeast(0L)
            lastProgressNanos = now
            smoothedUnitsPerSecond = 0.0
            return@synchronized
        }
        val delta = newValue - lastProgressValue
        val elapsedSeconds = (now - lastProgressNanos) / 1_000_000_000.0
        if (delta > 0L && elapsedSeconds >= 0.05) {
            val instantaneousRate = delta / elapsedSeconds
            smoothedUnitsPerSecond = if (smoothedUnitsPerSecond == 0.0) {
                instantaneousRate
            } else {
                (smoothedUnitsPerSecond * 0.7) + (instantaneousRate * 0.3)
            }
            lastProgressValue = newValue
            lastProgressNanos = now
        }
    }

    private fun resetProgressClock(keepRate: Boolean) = synchronized(progressMonitor) {
        lastProgressValue = observableBytesDownloaded
        lastProgressNanos = System.nanoTime()
        if (!keepRate) smoothedUnitsPerSecond = 0.0
    }

    private fun updateElapsedForStatusChange(old: DownloadStatus, new: DownloadStatus) =
        synchronized(elapsedMonitor) {
            val wasTimed = old.isTimedStage()
            val willBeTimed = new.isTimedStage()
            if (wasTimed && !willBeTimed) {
                activeSinceNanos?.let { accumulatedActiveNanos += System.nanoTime() - it }
                activeSinceNanos = null
            } else if (!wasTimed && willBeTimed) {
                activeSinceNanos = System.nanoTime()
            }
        }

    companion object {
        private val connectionCleanup = java.util.concurrent.Executors.newSingleThreadExecutor {
            Thread(it, "Miowser-transfer-close")
        }
        private val idCounter = AtomicLong(0)
        fun nextId(): Long = idCounter.getAndIncrement()

        /** Bumps the counter past a restored download's old id, so new downloads never collide with it. */
        fun ensureNextIdAbove(id: Long) {
            idCounter.updateAndGet { current -> maxOf(current, id + 1) }
        }
    }
}

private fun DownloadStatus.isTimedStage(): Boolean =
    this == DownloadStatus.DOWNLOADING || this == DownloadStatus.MUXING

/** Signals safe ownership release, never a retryable network failure. */
internal class DownloadYieldException : RuntimeException()
