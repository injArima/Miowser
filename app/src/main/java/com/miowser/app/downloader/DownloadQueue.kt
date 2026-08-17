package com.miowser.app.downloader

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
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

    @Volatile private var restored = false

    fun enqueue(context: Context, download: Download) {
        downloads.add(0, download) // newest first
        persist(context)
        startAndDispatch(context, download)
    }

    fun cancel(download: Download) {
        download.isCanceled = true
    }

    /**
     * Re-runs a FAILED or CANCELED download in place — same list entry, same id, no duplicate
     * row. Safe to call on any of the three downloaders: each one deletes its own cache dir in a
     * `finally` block at the end of every run (keyed by download.id, so a stale one never lingers
     * between attempts) and opens a fresh MediaStore output on every call, so there's no leftover
     * state from the failed attempt to clean up here beyond the Download object's own fields.
     *
     * Caveat this doesn't paper over: a download restored from disk after a restart has no
     * userAgent/cookies/referer (see DownloadStore's doc comment on why those aren't persisted).
     * Retrying it still goes out over the network with whatever it has — which for most direct
     * file/manifest URLs is enough, but for anything that actually needed those headers, retry
     * will just fail again with a real (if less informative) HTTP/connection error rather than a
     * fabricated "can't retry" message. That's judged an acceptable, honest limitation rather than
     * one worth adding heuristics to preempt.
     */
    fun retry(context: Context, download: Download) {
        if (download.status != DownloadStatus.FAILED && download.status != DownloadStatus.CANCELED) return
        download.status = DownloadStatus.QUEUED
        download.bytesDownloaded = 0L
        download.totalBytes = -1L
        download.errorMessage = null
        download.isCanceled = false
        persist(context)
        startAndDispatch(context, download)
    }

    private fun startAndDispatch(context: Context, download: Download) {
        DownloadForegroundService.start(context.applicationContext)
        executor.execute {
            when (download.kind) {
                DownloadKind.DIRECT_FILE -> DirectFileDownloader.run(context.applicationContext, download)
                DownloadKind.HLS -> HlsDownloader.run(context.applicationContext, download)
                DownloadKind.DASH -> DashDownloader.run(context.applicationContext, download)
            }
            persist(context)
        }
    }

    fun hasActiveWork(): Boolean = downloads.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
    }

    /**
     * Loads the persisted list into [downloads]. Safe to call repeatedly (e.g. from a Composable
     * LaunchedEffect that could recompose) — only does real work once per process.
     *
     * There's no resume support anywhere in the download engine, so any record left in a
     * non-terminal status (QUEUED/DOWNLOADING/MUXING — meaning the process died mid-download)
     * is reclassified to FAILED here rather than shown as if it were still progressing.
     */
    fun restore(context: Context) {
        if (restored) return
        restored = true
        val saved = DownloadStore.load(context)
        if (saved.isEmpty()) return
        for (record in saved) {
            Download.ensureNextIdAbove(record.id)
            val interrupted = record.status == DownloadStatus.QUEUED ||
                record.status == DownloadStatus.DOWNLOADING ||
                record.status == DownloadStatus.MUXING
            val download = Download(
                id = record.id,
                sourceUrl = record.sourceUrl,
                pageUrl = record.pageUrl,
                userAgent = "",
                referer = "",
                cookies = null,
                kind = record.kind,
                displayName = record.displayName,
                outputBaseName = record.outputBaseName
            )
            download.status = if (interrupted) DownloadStatus.FAILED else record.status
            download.errorMessage = if (interrupted) "Interrupted — app was closed before this finished" else record.errorMessage
            download.bytesDownloaded = record.bytesDownloaded
            download.totalBytes = record.totalBytes
            downloads += download
        }
    }

    /** Snapshots [downloads] to SharedPreferences. Cheap enough to call on every status change. */
    fun persist(context: Context) {
        val snapshot = downloads.map { d ->
            SavedDownload(
                id = d.id,
                sourceUrl = d.sourceUrl,
                pageUrl = d.pageUrl,
                kind = d.kind,
                displayName = d.displayName,
                outputBaseName = d.outputBaseName,
                status = d.status,
                bytesDownloaded = d.bytesDownloaded,
                totalBytes = d.totalBytes,
                errorMessage = d.errorMessage
            )
        }
        DownloadStore.save(context, snapshot)
    }
}
