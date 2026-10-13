package com.miowser.app.benchmark

import android.os.Bundle
import android.os.StrictMode
import android.webkit.CookieManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.miowser.app.browser.PrivateSessionStore
import com.miowser.app.downloader.*
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.MediaLibrary
import com.miowser.app.settings.AppSettings
import com.miowser.app.startup.AppStartup
import com.miowser.app.startup.StartupState
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class StartupBarrierProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val label = TextView(this).apply { text = "Checking startup barriers" }
        setContentView(label)
        lifecycleScope.launch {
            if (intent.getBooleanExtra("privacy", false)) {
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                suspendCancellableCoroutine { continuation ->
                    cookies.setCookie("https://startup.example.invalid", "private_probe=1; Path=/; Secure") {
                        check(it)
                        continuation.resume(Unit)
                    }
                }
                check(cookies.getCookie("https://startup.example.invalid")?.contains("private_probe=1") == true)
                withContext(Dispatchers.IO) { check(PrivateSessionStore.setActive(applicationContext, true)) }
                AppStartup.start(applicationContext)
                withTimeout(30_000) { while (AppStartup.state !is StartupState.Ready) delay(10) }
                check(withContext(Dispatchers.IO) { PrivateSessionStore.wasInterrupted(applicationContext) })
                coroutineScope {
                    repeat(2) { launch { AppStartup.ensurePrivacy(applicationContext) } }
                }
                check(cookies.getCookie("https://startup.example.invalid").isNullOrEmpty())
                check(!withContext(Dispatchers.IO) { PrivateSessionStore.wasInterrupted(applicationContext) })
                label.text = "Private startup barrier verified"
            } else {
                withContext(Dispatchers.IO) {
                    val records = (1L..100L).map { id ->
                        SavedDownload(id, "https://example.invalid/$id", "", DownloadKind.DIRECT_FILE,
                            "Interrupted $id", "probe-$id", DownloadStatus.DOWNLOADING, 0, 100, null)
                    }
                    check(getSharedPreferences("downloads", MODE_PRIVATE).edit()
                        .putString("downloads", DownloadCodec.encode(records)).commit())
                    for (id in 1L..3L) check(DownloadRecoveryStore.save(applicationContext,
                        DownloadRecoveryPlan(downloadId = id, transferKind = RecoveryTransferKind.DIRECT_RANGES,
                            sourceFingerprint = null, etag = null, lastModified = null, totalLength = 100,
                            outputFileName = "probe-$id.mp4", mimeType = "video/mp4", itemFingerprint = null,
                            itemCount = 0, ranges = listOf(RecoveryRange(0, 99, 25)))))
                }
                val previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyDeath().build())
                try {
                    AppSettings.loadAsync(applicationContext)
                    LibraryFolders.restoreAsync(applicationContext)
                    MediaLibrary.restoreAsync(applicationContext)
                    DownloadQueue.restoreAsync(applicationContext)
                    check(DownloadQueue.downloads.size == 100)
                    val first = DownloadQueue.downloads.first()
                    check(first.recoveryChecking && first.status != DownloadStatus.PAUSED)
                    DownloadQueue.retry(applicationContext, first)
                    check(first.recoveryChecking && first.status != DownloadStatus.QUEUED)
                    val cancelled = DownloadQueue.downloads[1]
                    val deleted = DownloadQueue.downloads[2]
                    DownloadQueue.cancel(applicationContext, cancelled)
                    DownloadQueue.delete(applicationContext, deleted, false)
                    DownloadQueue.restoreAsync(applicationContext)
                    check(DownloadQueue.downloads.size == 99)
                    withTimeout(30_000) { while (DownloadQueue.downloads.any { it.recoveryChecking }) delay(10) }
                    check(first.status == DownloadStatus.PAUSED && first.bytesDownloaded == 25L)
                    check(cancelled.status == DownloadStatus.CANCELED)
                    check(DownloadQueue.downloads.none { it.id == deleted.id })
                    check(DownloadQueue.downloads.last().status == DownloadStatus.FAILED)
                } finally { StrictMode.setThreadPolicy(previous) }
                label.text = "Asynchronous recovery verified"
            }
        }
    }
}
