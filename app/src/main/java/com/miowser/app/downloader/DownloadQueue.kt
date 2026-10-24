package com.miowser.app.downloader

import android.content.Context
import kotlinx.coroutines.launch
import com.miowser.app.persistence.Persistence
import androidx.compose.runtime.snapshots.Snapshot
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.runtime.mutableStateListOf
import com.miowser.app.library.LibraryThumbnailStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val MAX_AUTOMATIC_DOWNLOAD_RETRIES = 3
private const val RETRY_WAIT_SLICE_MS = 200L

/**
 * The download engine's single entry point — mirrors the AdBlock/MediaProbe singleton-object
 * pattern already used elsewhere in this codebase. Holds the Compose-observable list the UI
 * reads directly; actual work happens on a small fixed pool so a handful of mutableDownloads can run
 * concurrently without unbounded thread growth.
 */
object DownloadQueue {
    private val mutableDownloads = mutableStateListOf<Download>()
    val downloads: List<Download> get() = mutableDownloads
    private val executor = java.util.concurrent.ThreadPoolExecutor(2, 2, 0L,
        java.util.concurrent.TimeUnit.MILLISECONDS, java.util.concurrent.LinkedBlockingQueue())
    private val queuedTasks = ConcurrentHashMap<Long, Runnable>()
    private val maintenance = Executors.newSingleThreadExecutor()
    private val cleanupGenerations = ConcurrentHashMap<Long, Any>()
    private val activeIds = ConcurrentHashMap.newKeySet<Long>()
    private var serviceStopping = false
    private val preparations = mutableMapOf<Long, kotlinx.coroutines.Job>()

    /** A transient FAILED status may still be in the automatic retry loop. */
    fun isTransferActive(id: Long): Boolean = activeIds.contains(id)

    fun relinkCompleted(context: Context, download: Download, uri: Uri) {
        Persistence.requireOwner()
        val current = mutableDownloads.firstOrNull { it.id == download.id } ?: return
        if (current.status != DownloadStatus.COMPLETED) return
        val old = current.savedUri
        current.savedUri = uri
        DownloadedFileInspector.forget(uri)
        persist(context)
        old?.let {
            DownloadedFileInspector.forget(it)
            com.miowser.app.library.MediaLibrary.relinkLocal(context, it.toString(), uri.toString())
        }
    }

    @Volatile private var restored = false

    fun enqueue(context: Context, download: Download) {
        Persistence.requireOwner()
        serviceStopping = false
        mutableDownloads.add(0, download) // newest first
        // Keep the accepted queued job alive while its initial durable record is written.
        DownloadForegroundService.start(context.applicationContext)
        persist(context) { startAndDispatch(context, download) }
    }

    fun pause(context: Context, download: Download) {
        Persistence.requireOwner()
        if (download.requestPause()) {
            removeQueuedTask(download)
            persist(context)
        }
    }

    fun resume(context: Context, download: Download) {
        Persistence.requireOwner()
        if (download.recoveryChecking) return
        if (download.status != DownloadStatus.PAUSED) return
        serviceStopping = false
        if (activeIds.contains(download.id)) {
            if (download.requestResume()) {
                DownloadForegroundService.start(context.applicationContext)
                persist(context)
            }
        } else {
            download.requestResume()
            DownloadForegroundService.start(context.applicationContext)
            persist(context) { startAndDispatch(context, download) }
        }
    }

    private fun removeQueuedTask(download: Download) {
        preparations.remove(download.id)?.cancel()
        val task = queuedTasks[download.id] ?: return
        if (executor.remove(task)) {
            queuedTasks.remove(download.id, task)
            activeIds.remove(download.id)
        }
    }

    fun pauseAll(context: Context) {
        Persistence.requireOwner()
        mutableDownloads.forEach { download ->
            if (download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.DOWNLOADING) {
                download.requestPause()
                removeQueuedTask(download)
            }
        }
        persist(context)
    }

    fun resumeAll(context: Context) {
        Persistence.requireOwner()
        serviceStopping = false
        val resumed = mutableDownloads.filter { it.status == DownloadStatus.PAUSED }
        resumed.forEach { it.requestResume() }
        if (resumed.isNotEmpty()) DownloadForegroundService.start(context.applicationContext)
        persist(context) { resumed.forEach { startAndDispatch(context, it) } }
    }

    fun cancel(context: Context, download: Download) {
        Persistence.requireOwner()
        download.recoveryChecking = false
        download.requestCancel()
        removeQueuedTask(download)
        if (!activeIds.contains(download.id)) {
            download.status = DownloadStatus.CANCELED
            persist(context)
            cleanup(context, listOf(download), deleteFile = false, removeThumbnail = false)
        }
    }

    /**
     * Re-runs a FAILED or CANCELED download in place — same list entry, same id, no duplicate
     * row. Verified recovery artifacts are retained, allowing a failed range/segment transfer to
     * continue. The User-Agent and Referer come from the persisted presentation record; current
     * cookies are reacquired just before dispatch so credentials are never stored long-term.
     */
    fun retry(context: Context, download: Download) {
        Persistence.requireOwner()
        if (download.recoveryChecking || activeIds.contains(download.id)) return
        serviceStopping = false
        if (download.status != DownloadStatus.FAILED && download.status != DownloadStatus.CANCELED) return
        if (download.kind == DownloadKind.WEBVIEW_RESOURCE && download.rendererTransfer == null) {
            download.errorMessage = "Return to the original page to download this page-generated file again"
            download.status = DownloadStatus.FAILED
            persist(context)
            return
        }
        download.recordSaved = false
        download.status = DownloadStatus.QUEUED
        download.errorMessage = null
        download.resetConnectionRetries()
        download.isCanceled = false
        download.resetElapsedTime()
        download.threadProgress.clear()
        persist(context) { startAndDispatch(context, download) }
    }

    private fun startAndDispatch(context: Context, download: Download) {
        Persistence.requireOwner()
        if (com.miowser.app.persistence.RestoreWriteFence.frozen || serviceStopping) return
        if (mutableDownloads.none { it === download } || download.isCanceled ||
            download.status == DownloadStatus.PAUSED || !activeIds.add(download.id)) return
        val appContext = context.applicationContext
        // Serialize this handoff with cleanup. Invalidate old cleanup on its own disk owner
        // before posting the new worker, so retry cannot race a queued recovery deletion.
        Persistence.submit("download-dispatch-${download.id}") {
            cleanupGenerations.remove(download.id)
            Persistence.onOwner {
                if (mutableDownloads.none { it === download } || download.isCanceled ||
                    download.status == DownloadStatus.PAUSED) {
                    activeIds.remove(download.id)
                } else com.miowser.app.startup.AppStartup.scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        var prepared = false
                        for (attempt in 0..MAX_AUTOMATIC_DOWNLOAD_RETRIES) {
                            if (download.isCanceled || download.status == DownloadStatus.PAUSED || serviceStopping) break
                            try {
                                com.miowser.app.startup.AppStartup.ensurePrivacy(appContext)
                                prepared = true
                                break
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                if (attempt == MAX_AUTOMATIC_DOWNLOAD_RETRIES) break
                                download.recordConnectionRetry()
                                kotlinx.coroutines.delay(1_000L shl attempt)
                            }
                        }
                        if (!prepared) {
                            activeIds.remove(download.id)
                            if (!download.isCanceled && download.status != DownloadStatus.PAUSED && !serviceStopping) {
                                download.status = DownloadStatus.FAILED
                                download.errorMessage = "Couldn’t prepare the download after retries"
                                persist(appContext)
                            }
                            return@launch
                        }
                        if (mutableDownloads.any { it === download } && !download.isCanceled && download.status != DownloadStatus.PAUSED) {
                            dispatchOnOwner(appContext, download)
                        } else activeIds.remove(download.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { activeIds.remove(download.id); throw cancelled }
                    catch (_: Exception) {
                        activeIds.remove(download.id)
                        download.status = DownloadStatus.FAILED
                        download.errorMessage = "Couldn’t prepare the download. Please retry."
                        persist(appContext)
                    } finally {
                        preparations.remove(download.id)
                        if (download.yieldRequested && !download.isCanceled &&
                            download.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) {
                            startAndDispatch(appContext, download)
                        }
                    }
                }.also { preparations[download.id] = it; it.start() }
            }
            true
        }
    }

    private fun dispatchOnOwner(context: Context, download: Download) {
        Persistence.requireOwner()
        if (serviceStopping || com.miowser.app.persistence.RestoreWriteFence.frozen || download.isCanceled || download.status == DownloadStatus.PAUSED) {
            activeIds.remove(download.id)
            return
        }
        if (download.cookies.isNullOrBlank()) {
            download.cookies = CookieManager.getInstance().getCookie(download.sourceUrl)
                ?: CookieManager.getInstance().getCookie(download.pageUrl)
        }
        DownloadForegroundService.start(context.applicationContext)
        val task = Runnable {
            queuedTasks.remove(download.id)
            download.beginAttempt()
            try {
                var attempts = 0
                while (!download.isCanceled) {
                    try {
                        when (download.kind) {
                            DownloadKind.DIRECT_FILE -> DirectFileDownloader.run(context.applicationContext, download)
                            DownloadKind.HLS -> HlsDownloader.run(context.applicationContext, download)
                            DownloadKind.DASH -> DashDownloader.run(context.applicationContext, download)
                            DownloadKind.WEBVIEW_RESOURCE -> download.rendererTransfer?.invoke(download)
                                ?: run {
                                    download.errorMessage = "The originating page is no longer available"
                                    download.status = DownloadStatus.FAILED
                                }
                        }
                    } catch (_: DownloadYieldException) {
                        break
                    } catch (e: Exception) {
                        if (download.yieldRequested || download.isCanceled) break
                        download.errorMessage = e.message ?: "Download failed"
                        download.status = DownloadStatus.FAILED
                    }
                    if (download.status == DownloadStatus.COMPLETED) {
                        DownloadedFileInspector.forget(download.savedUri)
                        download.errorMessage = null
                        break
                    }
                    // A blob that the owning document cannot expose as a file is not a
                    // connection failure. Re-running the same renderer fetch cannot turn an
                    // MSE stream into a file, and would only keep its WebView awake longer.
                    if (download.kind == DownloadKind.WEBVIEW_RESOURCE &&
                        (download.rendererSourceUnavailable ||
                            download.errorMessage?.startsWith("This blob cannot be read as a file") == true)
                    ) break
                    if (download.status != DownloadStatus.FAILED ||
                        download.isCanceled || attempts >= MAX_AUTOMATIC_DOWNLOAD_RETRIES
                    ) break
                    attempts++
                    val previousError = download.errorMessage ?: "Download failed"
                    download.recordConnectionRetry()
                    download.errorMessage = "Retrying ($attempts/$MAX_AUTOMATIC_DOWNLOAD_RETRIES): $previousError"
                    download.enterStage(DownloadStatus.DOWNLOADING)
                    val waitMs = 1_000L shl (attempts - 1)
                    var waitedMs = 0L
                    while (waitedMs < waitMs && !download.isCanceled) {
                        download.awaitIfPaused()
                        if (download.isCanceled) break
                        val slice = minOf(RETRY_WAIT_SLICE_MS, waitMs - waitedMs)
                        Thread.sleep(slice)
                        waitedMs += slice
                    }
                }
            } catch (_: DownloadYieldException) {
                // All children have joined and closed their resources before ownership returns.
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                if (!download.isCanceled) {
                    download.errorMessage = "Download was interrupted"
                    download.status = DownloadStatus.FAILED
                }
            } finally {
                if (download.isCanceled && download.status != DownloadStatus.COMPLETED) {
                    download.status = DownloadStatus.CANCELED
                }
                Persistence.onOwner {
                    activeIds.remove(download.id)
                    download.publishProgress(force = true)
                    persist(context) {
                        if (download.yieldRequested && !download.isCanceled &&
                            download.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) {
                            startAndDispatch(context, download)
                        }
                    }
                }
            }
        }
        queuedTasks[download.id] = task
        executor.execute(task)
    }

    fun checkpointForServiceStop(context: Context, onStopped: () -> Unit = {}) {
        Persistence.requireOwner()
        serviceStopping = true
        mutableDownloads.forEach { it.requestServiceCheckpoint(activeIds.contains(it.id)); removeQueuedTask(it) }
        persist(context)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val wait = object : Runnable {
            override fun run() {
                if (activeIds.isEmpty()) onStopped() else handler.postDelayed(this, 100)
            }
        }
        handler.post(wait)
    }

    fun isQuiescent(): Boolean = activeIds.isEmpty()

    fun hasActiveWork(): Boolean = mutableDownloads.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING
    }

    /**
     * Loads the persisted list into [mutableDownloads]. Safe to call repeatedly (e.g. from a Composable
     * LaunchedEffect that could recompose) — only does real work once per process.
     *
     * Interrupted work with a valid private recovery plan restores paused and can continue from
     * verified ranges/segments. Records without a usable plan retain the older safe failure path.
     */
    fun restore(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        publishRestored(context, DownloadStore.load(context))
    }

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DownloadStore.load(context) }
        if (!restored) publishRestored(context, saved)
    }

    private fun publishRestored(context: Context, saved: List<SavedDownload>) {
        if (saved.isEmpty()) {
            restored = true
            LibraryThumbnailStore.pruneDownloadedAsync(context, emptyList())
            return
        }
        val restoredDownloads = saved.map { record ->
            Download.ensureNextIdAbove(record.id)
            val interrupted = record.status == DownloadStatus.QUEUED ||
                record.status == DownloadStatus.DOWNLOADING ||
                record.status == DownloadStatus.MUXING
            val needsVerification = interrupted || record.status == DownloadStatus.PAUSED
            val download = Download(
                id = record.id,
                sourceUrl = record.sourceUrl,
                pageUrl = record.pageUrl,
                userAgent = record.userAgent,
                referer = record.referer,
                cookies = null,
                kind = record.kind,
                displayName = record.displayName,
                outputBaseName = record.outputBaseName,
                suggestedExtension = record.suggestedExtension,
                preferredOutputBaseName = record.preferredOutputBaseName
            )
            download.status = record.status
            download.errorMessage = record.errorMessage
            download.bytesDownloaded = record.bytesDownloaded
            download.totalBytes = record.totalBytes
            download.restoreElapsedTime(record.elapsedActiveMillis)
            download.recoveryChecking = needsVerification
            if (needsVerification) download.startupRecord = record
            if (needsVerification) {
                download.status = DownloadStatus.FAILED
                download.errorMessage = "Checking interrupted download…"
            }
            // Only ever meaningful for a record that actually finished — restricting on
            // record.status here (rather than trusting a non-null savedUri on its own) means a
            // malformed/tampered record can't resurrect a "playable" download that was never
            // really COMPLETED.
            if (record.status == DownloadStatus.COMPLETED) {
                download.savedUri = record.savedUri?.let { uri -> runCatching { Uri.parse(uri) }.getOrNull() }
            }
            download.recordSaved = true
            download.publishProgress(force = true)
            download
        }
        mutableDownloads.addAll(restoredDownloads)
        restored = true
        // No service or recovery directory scan is needed to display saved records.
        com.miowser.app.startup.AppStartup.scope.launch {
            restoredDownloads.filter { it.recoveryChecking }.forEach { download ->
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        DownloadRecoveryStore.load(context.applicationContext, download.id)?.let {
                            Triple(it.completedUnits(context), it.progressTotal, it.elapsedActiveMillis)
                        }
                    }
                }
                if (mutableDownloads.any { it === download } && download.recoveryChecking) {
                    download.recoveryChecking = false
                    val recovery = result.getOrNull()
                    if (recovery != null && download.kind != DownloadKind.WEBVIEW_RESOURCE) {
                        download.bytesDownloaded = recovery.first
                        download.totalBytes = recovery.second
                        download.restoreElapsedTime(maxOf(download.elapsedActiveMillis(), recovery.third))
                        download.restorePausedState()
                        download.status = DownloadStatus.PAUSED
                        download.errorMessage = "Paused after restart — ready to resume"
                    } else {
                        download.status = DownloadStatus.FAILED
                        download.errorMessage = if (result.isFailure) "Recovery check failed — retry to recheck"
                            else "Interrupted — no verified recovery state was available"
                    }
                }
            }
        }
        LibraryThumbnailStore.pruneDownloadedAsync(
            context,
            mutableDownloads.filter { it.status == DownloadStatus.COMPLETED }.mapNotNull(Download::savedUri)
        )
    }

    fun replaceAndRestart(
        context: Context,
        old: Download,
        replacement: Download,
        deleteExistingFile: Boolean = false
    ) {
        Persistence.requireOwner()
        val index = mutableDownloads.indexOfFirst { it.id == old.id }
        if (index < 0) return
        old.requestCancel()
        mutableDownloads[index] = replacement
        persist(context) {
            cleanup(context, listOf(old), deleteExistingFile, removeThumbnail = deleteExistingFile) {
                startAndDispatch(context, replacement)
            }
        }
    }

    /** Switches to a freshly captured link without deleting completed range/segment work. */
    fun refreshLink(context: Context, old: Download, candidate: Download): Boolean {
        Persistence.requireOwner()
        val index = mutableDownloads.indexOfFirst { it === old }
        if (index < 0) return false
        val canReuse = old.status != DownloadStatus.COMPLETED &&
            old.kind == candidate.kind && old.kind != DownloadKind.WEBVIEW_RESOURCE
        val replacement = Download(
            id = old.id,
            sourceUrl = candidate.sourceUrl,
            pageUrl = old.pageUrl.ifBlank { candidate.pageUrl },
            userAgent = candidate.userAgent,
            referer = candidate.referer,
            cookies = candidate.cookies,
            kind = candidate.kind,
            displayName = old.displayName,
            outputBaseName = old.outputBaseName,
            suggestedExtension = old.suggestedExtension,
            preferredOutputBaseName = old.preferredOutputBaseName
                ?: old.displayName.substringBeforeLast('.', old.displayName)
        )
        if (!canReuse) {
            replaceAndRestart(context, old, replacement, deleteExistingFile = old.status == DownloadStatus.COMPLETED)
            return false
        }
        replacement.restoreElapsedTime(old.elapsedActiveMillis())
        replacement.bytesDownloaded = old.bytesDownloaded
        replacement.totalBytes = old.totalBytes
        old.preserveRecoveryOnCancel = true
        old.requestCancel()
        mutableDownloads[index] = replacement
        persist(context) {
            // The old worker must close its file handles before the new URL probes and reuses them.
            maintenance.execute {
                while (activeIds.contains(old.id)) Thread.sleep(25)
                Persistence.onOwner {
                    if (mutableDownloads.any { it === replacement }) startAndDispatch(context, replacement)
                }
            }
        }
        return true
    }

    fun delete(context: Context, download: Download, deleteFile: Boolean) {
        deleteAll(context, listOf(download), deleteFile)
    }

    fun deleteAll(context: Context, removed: Collection<Download>, deleteFile: Boolean = false) {
        Persistence.requireOwner()
        val selected = removed.toList()
        val ids = selected.map { it.id }.toSet()
        selected.forEach { it.requestCancel() }
        mutableDownloads.removeAll { it.id in ids }
        persist(context)
        cleanup(context, selected, deleteFile)
    }

    /** Wait for worker ownership to end off-main before deleting its recovery artifacts. */
    private fun cleanup(
        context: Context, removed: List<Download>, deleteFile: Boolean,
        removeThumbnail: Boolean = true, onFinished: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val token = Any()
        val records = removed.map { it.id to it }
        records.forEach { (id, _) -> cleanupGenerations[id] = token }
        maintenance.execute {
            while (records.any { (id, _) -> activeIds.contains(id) }) Thread.sleep(25)
            Persistence.submit("download-cleanup-${java.util.UUID.randomUUID()}") {
                records.forEach { (id, download) ->
                    // An old failed cleanup must not erase a later replacement's artifacts.
                    if (cleanupGenerations[id] === token) {
                        // A worker can publish its output just as cancellation arrives. Read the
                        // final URI after it releases ownership so Stream removes that file too.
                        val uri = download.savedUri
                        DownloadRecoveryStore.clear(appContext, id)
                        check(!DownloadRecoveryStore.directory(appContext, id).exists())
                        if (removeThumbnail) {
                            LibraryThumbnailStore.removeDownloadedThumbnail(appContext, uri)
                            DownloadedFileInspector.forget(uri)
                        }
                        if (deleteFile && uri != null) MediaStoreOutput.delete(appContext, uri)
                        cleanupGenerations.remove(id, token)
                    }
                }
                Persistence.onOwner(onFinished)
                true
            }
        }
    }

    /** Worker callers post to the list owner; only immutable records reach the disk writer. */
    fun persist(context: Context, onSaved: () -> Unit = {}) {
        val appContext = context.applicationContext
        Persistence.onOwner { persistOnOwner(appContext, onSaved) }
    }

    private fun persistOnOwner(context: Context, onSaved: () -> Unit) {
        Persistence.requireOwner()
        val view = Snapshot.takeSnapshot()
        val snapshot = try { view.enter { mutableDownloads.map { d ->
            if (d.recoveryChecking) d.startupRecord!! else SavedDownload(
                id = d.id,
                // Renderer URLs are either document-scoped (blob:) or contain the payload itself
                // (data:). Persist only the scheme: a completed file has savedUri, while an
                // interrupted one cannot safely resume after its page/process is gone anyway.
                sourceUrl = if (d.kind == DownloadKind.WEBVIEW_RESOURCE) {
                    rendererResourceKind(d.sourceUrl)?.name?.lowercase()?.plus(":") ?: "renderer:"
                } else {
                    d.sourceUrl
                },
                pageUrl = d.pageUrl,
                kind = d.kind,
                displayName = d.displayName,
                outputBaseName = d.outputBaseName,
                status = d.status,
                bytesDownloaded = d.bytesDownloaded,
                totalBytes = d.totalBytes,
                errorMessage = d.errorMessage,
                savedUri = d.savedUri?.toString(),
                suggestedExtension = d.suggestedExtension,
                elapsedActiveMillis = d.elapsedActiveMillis(),
                preferredOutputBaseName = d.preferredOutputBaseName,
                userAgent = d.userAgent,
                referer = d.referer
            )
        }
        } } finally { view.dispose() }
        val completed = snapshot.filter { it.status == DownloadStatus.COMPLETED }.map { it.id }.toSet()
        val owners = mutableDownloads.filter { it.id in completed }.associateBy { it.id }
        DownloadStore.save(context, snapshot) {
            mutableDownloads.filter { owners[it.id] === it && it.status == DownloadStatus.COMPLETED }
                .forEach { it.recordSaved = true }
            onSaved()
        }
    }
}
