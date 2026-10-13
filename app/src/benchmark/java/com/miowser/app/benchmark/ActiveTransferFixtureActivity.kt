package com.miowser.app.benchmark

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.settings.AppSettings
import com.miowser.app.startup.AppStartup
import kotlinx.coroutines.launch

/** Starts a real native fixture transfer, never a synthetic progress timer. Benchmark APK only. */
class ActiveTransferFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val text = TextView(this).apply { this.text = "Preparing fixture transfer" }
        setContentView(text)
        AppStartup.scope.launch {
            AppSettings.loadAsync(applicationContext)
            DownloadQueue.restoreAsync(applicationContext)
            val download = checkNotNull(DownloadQueue.downloads.firstOrNull {
                it.sourceUrl == "http://127.0.0.1:8765/slow/transfer.bin"
            }) { "Seed with fixture_server and active_transfers first" }
            DownloadQueue.retry(this@ActiveTransferFixtureActivity, download)
            text.text = "Fixture transfer requested"
        }
    }
}
