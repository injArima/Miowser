package com.miowser.app.benchmark

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.miowser.app.browser.*
import com.miowser.app.settings.AppSettings
import com.miowser.app.startup.AppStartup
import com.miowser.app.startup.StartupState
import com.miowser.app.ui.theme.MiowserTheme
import kotlinx.coroutines.*

/** Only the benchmark APK can inspect the real BrowserScreen's injected engine owner. */
class TabControllerProbeActivity : ComponentActivity() {
    private val controller = TabController()
    private var verdict by mutableStateOf("")
    private var rememberedView: WebView? = null
    private var rememberedId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val records = when (intent.getStringExtra("mode")) {
                    "home" -> listOf(SavedTab(BROWSER_HOME_URL, -1, title = "Home"))
                    "child" -> listOf(SavedTab(page("Parent"), -1, title = "Parent"),
                        SavedTab(page("Child"), 0, title = "Child"))
                    else -> null
                }
                if (records != null) check(getSharedPreferences("browser_session", MODE_PRIVATE).edit()
                    .putString("tabs", TabSessionCodec.encode(records))
                    .putInt("active_index", if (records.size == 2) 1 else 0).commit())
            }
            AppStartup.start(applicationContext)
            setContent {
                MiowserTheme {
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        (AppStartup.state as? StartupState.Ready)?.let {
                            BrowserScreen(initialHubSettings = it.settings, tabController = controller)
                        }
                        Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().background(MaterialTheme.colorScheme.surface).padding(2.dp)) {
                            Text("Records ${controller.tabs.size}; engines ${controller.liveEngineCount}", style = MaterialTheme.typography.labelSmall)
                            if (verdict.isNotEmpty()) Text(verdict, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.getStringExtra("action")) {
            "queued-session" -> lifecycleScope.launch {
                val original = TabSessionStore.snapshot(controller.tabs, controller.tabs.first().id)
                val gate = java.util.concurrent.CountDownLatch(1)
                val entered = java.util.concurrent.CountDownLatch(1)
                com.miowser.app.persistence.Persistence.submit("step6-gate") {
                    entered.countDown()
                    gate.await(20, java.util.concurrent.TimeUnit.SECONDS)
                }
                try {
                    check(withContext(Dispatchers.IO) { entered.await(10, java.util.concurrent.TimeUnit.SECONDS) })
                    val latest = listOf(SavedTab(page("Queued session"), -1)) to 0
                    TabSessionStore.saveSnapshot(this@TabControllerProbeActivity, latest)
                    check(withContext(Dispatchers.IO) { TabSessionStore.load(this@TabControllerProbeActivity) } == latest)
                    TabSessionStore.saveSnapshot(this@TabControllerProbeActivity, emptyList<SavedTab>() to 0)
                    check(withContext(Dispatchers.IO) { TabSessionStore.load(this@TabControllerProbeActivity) } == null)
                    verdict = "Queued session preserved"
                } finally {
                    TabSessionStore.saveSnapshot(this@TabControllerProbeActivity, original)
                    gate.countDown()
                }
            }
            "remember" -> {
                val tab = controller.tabs.first { it.liveWebView != null }
                lifecycleScope.launch {
                    withTimeout(15_000) { while (tab.lastFinishedUrl == null) delay(10) }
                    rememberedId = tab.id
                    rememberedView = tab.liveWebView
                    rememberedView!!.evaluateJavascript("window.step6Sentinel='kept'; document.body.dataset.step6='kept'; 'saved'") {
                        check(it == "\"saved\""); verdict = "Page sentinel saved"
                    }
                }
            }
            "verify" -> {
                val tab = controller.tabs.first { it.id == rememberedId }
                check(tab.liveWebView === rememberedView)
                tab.liveWebView!!.evaluateJavascript("window.step6Sentinel + ':' + document.body.dataset.step6") {
                    check(it == "\"kept:kept\""); verdict = "Warm page preserved"
                }
            }
            "navigate" -> controller.tabs.single().navigateTo(page("Native Home navigation"))
            "private-navigate" -> controller.tabs.single { it.isPrivate }.navigateTo(page("Private probe"))
            "verify-private" -> lifecycleScope.launch {
                val tab = controller.tabs.single { it.isPrivate }
                withTimeout(15_000) { while (tab.lastFinishedUrl == null) delay(10) }
                check(com.miowser.app.history.HistoryLog.entries.none { it.url == tab.url })
                val saved = TabSessionStore.snapshot(controller.tabs, tab.id).first
                check(saved.size == 1 && saved.single().url == BROWSER_HOME_URL)
                verdict = "Private record excluded"
            }
            "preferences" -> {
                val count = controller.liveEngineCount
                controller.tabs.forEach { it.applyBrowserPreferences(AppSettings.current) }
                check(controller.liveEngineCount == count)
                verdict = "Dormant preferences preserved"
            }
        }
    }

    private fun page(title: String) = "data:text/html;base64," + android.util.Base64.encodeToString(
        "<html><head><title>$title</title></head><body>$title</body></html>".toByteArray(), android.util.Base64.NO_WRAP)
}
