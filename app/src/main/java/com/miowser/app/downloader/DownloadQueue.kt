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

    fun cancel(download: Download) {
        download.isCanceled = true
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
