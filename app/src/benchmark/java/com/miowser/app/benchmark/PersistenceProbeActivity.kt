package com.miowser.app.benchmark

import android.app.Activity
import android.os.Bundle
import android.os.StrictMode
import android.widget.TextView
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.history.HistoryLog
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.MediaLibrary
import com.miowser.app.persistence.Persistence
import com.miowser.app.settings.AppSettings

/** Isolated benchmark-only durability probe; never compiled into debug or release APKs. */
class PersistenceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val label = TextView(this).also { it.text = "Checking persistence…" }
        setContentView(label)
        MediaLibrary.restore(this)
        LibraryFolders.restore(this)
        HistoryLog.restore(this)
        BookmarkList.restore(this)
        DownloadQueue.restore(this)
        AppSettings.load(this)
        if (intent.getBooleanExtra("verify", false)) {
            check(MediaLibrary.entries.size == 120)
            check(MediaLibrary.entries.all { it.folderId == LibraryFolders.entries.single().id })
            check(HistoryLog.entries.single().title == "Latest title")
            check(HistoryLog.entries.single().visitCount == 100)
            check(BookmarkList.bookmarks.single().url == "https://step4.invalid/kept")
            check(DownloadQueue.downloads.size == 151)
            val completed = DownloadQueue.downloads.single { it.status == DownloadStatus.COMPLETED }
            check(completed.recordSaved && completed.savedUri != null)
            check(!AppSettings.current.adBlockEnabled)
            label.text = "Step 4 restart verified"
            return
        }
        check(MediaLibrary.entries.size == 500)
        val before = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskWrites().penaltyDeath().build())
        try {
            val removed = MediaLibrary.entries.takeLast(400)
            MediaLibrary.removeAll(this, removed)
            MediaLibrary.restoreRemoved(this, removed.take(20))
            val folder = checkNotNull(LibraryFolders.add(this, "Step 4 folder"))
            MediaLibrary.moveToFolder(this, MediaLibrary.entries.map { it.identityKey }.toSet(), folder.id)
            // A second restore during outstanding writes must not reload stale disk data.
            MediaLibrary.restore(this)
            check(MediaLibrary.entries.size == 120)
            HistoryLog.clear(this)
            repeat(100) { HistoryLog.recordVisit(this, "https://step4.invalid/history", "Visit $it") }
            // Keep the final count 100 while changing the title with the last visit.
            HistoryLog.clear(this)
            repeat(100) { HistoryLog.recordVisit(this, "https://step4.invalid/history", "Latest title") }
            BookmarkList.toggle(this, "https://step4.invalid/removed", "Remove me")
            BookmarkList.toggle(this, "https://step4.invalid/removed", "Remove me")
            BookmarkList.toggle(this, "https://step4.invalid/kept", "Keep me")
            DownloadQueue.deleteAll(this, DownloadQueue.downloads.take(50))
            AppSettings.update(this) { it.copy(adBlockEnabled = true) }
            AppSettings.update(this) { it.copy(adBlockEnabled = false) }
        } finally { StrictMode.setThreadPolicy(before) }
        // Exercise worker -> main snapshot -> disk commit -> completion acknowledgement.
        val completed = Download(
            id = 999_999L, sourceUrl = "blob:step4", pageUrl = "https://step4.invalid",
            userAgent = "", referer = "", cookies = "", kind = DownloadKind.WEBVIEW_RESOURCE,
            displayName = "Step 4 synthetic file", outputBaseName = "step4"
        )
        completed.rendererTransfer = { download ->
            val file = java.io.File(filesDir, "step4-synthetic.txt")
            file.writeText("Step 4 persistence fixture")
            download.savedUri = android.net.Uri.fromFile(file)
            download.status = DownloadStatus.COMPLETED
        }
        DownloadQueue.enqueue(this, completed)
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        val handler = android.os.Handler(mainLooper)
        val awaitCompletion = object : Runnable {
            override fun run() {
                if (completed.recordSaved) Persistence.afterWrites { label.text = "Step 4 changes saved" }
                else if (android.os.SystemClock.uptimeMillis() < deadline) handler.postDelayed(this, 20)
                else label.text = "Step 4 completion timed out"
            }
        }
        handler.post(awaitCompletion)
    }
}
