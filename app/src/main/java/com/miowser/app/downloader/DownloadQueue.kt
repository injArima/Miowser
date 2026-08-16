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

    fun enqueue(context: Context, download: Download) {
        downloads.add(0, download) // newest first
        DownloadForegroundService.start(context.applicationContext)
        executor.execute {
            when (download.kind) {
                DownloadKind.DIRECT_FILE -> DirectFileDownloader.run(context.applicationContext, download)
                DownloadKind.HLS -> HlsDownloader.run(context.applicationContext, download)
            }
        }
    }

    fun cancel(download: Download) {
        download.isCanceled = true
    }

    fun hasActiveWork(): Boolean = downloads.any {
        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
    }
}
