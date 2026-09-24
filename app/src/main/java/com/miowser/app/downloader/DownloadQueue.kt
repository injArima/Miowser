package com.miowser.app.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.runtime.mutableStateListOf
import com.miowser.app.library.LibraryThumbnailStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The download engine's single entry point — mirrors the AdBlock/MediaProbe singleton-object
 * pattern already used elsewhere in this codebase. Holds the Compose-observable list the UI
 * reads directly; actual work happens on a small fixed pool so a handful of downloads can run
 * concurrently without unbounded thread growth.
 */
object DownloadQueue {
    val downloads = mutableStateListOf<Download>()
    private val executor = Executors.newFixedThreadPool(2)
    private val activeIds = ConcurrentHashMap.newKeySet<Long>()

    @Volatile private var restored = false

    fun enqueue(context: Context, download: Download) {
        downloads.add(0, download) // newest first
        persist(context)
        startAndDispatch(context, download)
    }

    fun pause(context: Context, download: Download) {
        if (download.requestPause()) persist(context)
    }

    fun resume(context: Context, download: Download) {
        if (download.status != DownloadStatus.PAUSED) return
        if (activeIds.contains(download.id)) {
            if (download.requestResume()) persist(context)
        } else {
            download.requestResume()
            persist(context)
            startAndDispatch(context, download)
        }
    }

    fun pauseAll(context: Context) {
        downloads.forEach { download ->
            if (download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.DOWNLOADING) {
                download.requestPause()
            }
        }
        persist(context)
    }

    fun resumeAll(context: Context) {
        downloads.filter { it.status == DownloadStatus.PAUSED }.forEach { resume(context, it) }
    }

    fun cancel(context: Context, download: Download) {
        download.requestCancel()
        if (!activeIds.contains(download.id)) {
            DownloadRecoveryStore.clear(context, download.id)
            download.status = DownloadStatus.CANCELED
            persist(context)
        }
    }

    /**
     * Re-runs a FAILED or CANCELED download in place — same list entry, same id, no duplicate
     * row. Verified recovery artifacts are retained, allowing a failed range/segment transfer to
     * continue. The User-Agent and Referer come from the persisted presentation record; current
     * cookies are reacquired just before dispatch so credentials are never stored long-term.
     */
    fun retry(context: Context, download: Download) {
        if (download.status != DownloadStatus.FAILED && download.status != DownloadStatus.CANCELED) return
        if (download.kind == DownloadKind.WEBVIEW_RESOURCE && download.rendererTransfer == null) {
            download.errorMessage = "Return to the original page to download this page-generated file again"
            download.status = DownloadStatus.FAILED
            persist(context)
            return
        }
        download.status = DownloadStatus.QUEUED
        download.errorMessage = null
        download.isCanceled = false
        download.resetElapsedTime()
        download.threadProgress.clear()
        persist(context)
        startAndDispatch(context, download)
    }

    private fun startAndDispatch(context: Context, download: Download) {
        if (!activeIds.add(download.id)) return
        if (download.cookies.isNullOrBlank()) {
            download.cookies = CookieManager.getInstance().getCookie(download.sourceUrl)
                ?: CookieManager.getInstance().getCookie(download.pageUrl)
        }
        DownloadForegroundService.start(context.applicationContext)
        executor.execute {
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
            } finally {
                activeIds.remove(download.id)
                persist(context)
            }
        }
    }

    fun hasActiveWork(): Boolean = downloads.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING
    }

    /**
     * Loads the persisted list into [downloads]. Safe to call repeatedly (e.g. from a Composable
     * LaunchedEffect that could recompose) — only does real work once per process.
     *
     * Interrupted work with a valid private recovery plan restores paused and can continue from
     * verified ranges/segments. Records without a usable plan retain the older safe failure path.
     */
    fun restore(context: Context) {
        if (restored) return
        restored = true
        val saved = DownloadStore.load(context)
        if (saved.isEmpty()) {
            LibraryThumbnailStore.pruneDownloadedAsync(context, emptyList())
            return
        }
        for (record in saved) {
            Download.ensureNextIdAbove(record.id)
            val interrupted = record.status == DownloadStatus.QUEUED ||
                record.status == DownloadStatus.DOWNLOADING ||
                record.status == DownloadStatus.MUXING
            val recovery = DownloadRecoveryStore.load(context, record.id)
            val resumable = interrupted && recovery != null && record.kind != DownloadKind.WEBVIEW_RESOURCE
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
            download.status = when {
                resumable -> DownloadStatus.PAUSED
                interrupted -> DownloadStatus.FAILED
                else -> record.status
            }
            download.errorMessage = when {
                resumable -> "Paused after restart — ready to resume"
                interrupted -> "Interrupted — no verified recovery state was available"
                else -> record.errorMessage
            }
            download.bytesDownloaded = recovery?.completedUnits(context) ?: record.bytesDownloaded
            download.totalBytes = recovery?.progressTotal ?: record.totalBytes
            download.restoreElapsedTime(maxOf(record.elapsedActiveMillis, recovery?.elapsedActiveMillis ?: 0L))
            if (record.status == DownloadStatus.PAUSED || resumable) download.restorePausedState()
            // Only ever meaningful for a record that actually finished — restricting on
            // record.status here (rather than trusting a non-null savedUri on its own) means a
            // malformed/tampered record can't resurrect a "playable" download that was never
            // really COMPLETED.
            if (record.status == DownloadStatus.COMPLETED) {
                download.savedUri = record.savedUri?.let { uri -> runCatching { Uri.parse(uri) }.getOrNull() }
            }
            downloads += download
        }
        if (downloads.any { it.status == DownloadStatus.PAUSED }) {
            DownloadForegroundService.start(context.applicationContext)
        }
        LibraryThumbnailStore.pruneDownloadedAsync(
            context,
            downloads.filter { it.status == DownloadStatus.COMPLETED }.mapNotNull(Download::savedUri)
        )
    }

    fun replaceAndRestart(
        context: Context,
        old: Download,
        replacement: Download,
        deleteExistingFile: Boolean = false
    ) {
        val index = downloads.indexOfFirst { it.id == old.id }
        if (index < 0) return
        old.requestCancel()
        DownloadRecoveryStore.clear(context, old.id)
        if (deleteExistingFile) old.savedUri?.let { uri ->
            DownloadedFileInspector.forget(uri)
            LibraryThumbnailStore.removeDownloadedThumbnail(context, uri)
            MediaStoreOutput.delete(context, uri)
        }
        downloads[index] = replacement
        persist(context)
        if (activeIds.contains(old.id)) {
            // The old worker owns this record id until its cancellation cleanup finishes.
            // Queue a short handoff waiter rather than racing two writers under the same id.
            executor.execute {
                while (activeIds.contains(old.id)) Thread.sleep(25)
                startAndDispatch(context, replacement)
            }
        } else {
            startAndDispatch(context, replacement)
        }
    }

    fun delete(context: Context, download: Download, deleteFile: Boolean) {
        download.requestCancel()
        DownloadRecoveryStore.clear(context, download.id)
        LibraryThumbnailStore.removeDownloadedThumbnail(context, download.savedUri)
        DownloadedFileInspector.forget(download.savedUri)
        if (deleteFile) download.savedUri?.let { MediaStoreOutput.delete(context, it) }
        downloads.removeAll { it.id == download.id }
        persist(context)
    }

    /** Snapshots [downloads] to SharedPreferences. Cheap enough to call on every status change. */
    fun persist(context: Context) {
        val snapshot = downloads.map { d ->
            SavedDownload(
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
        DownloadStore.save(context, snapshot)
    }
}
