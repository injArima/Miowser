package com.miowser.app.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.miowser.app.browser.*
import com.miowser.app.history.HistoryLog
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibraryFolders
import com.miowser.app.startup.*
import com.miowser.app.ui.theme.MiowserTheme
import kotlinx.coroutines.*

/** Malformed preference is confined to the isolated benchmark application. */
class StartupProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        lifecycleScope.launch {
            if (savedInstanceState == null) withContext(Dispatchers.IO) {
                check(getSharedPreferences("hub_settings", MODE_PRIVATE).edit().putInt("library_sort", 7).commit())
            }
            setContent {
                MiowserTheme {
                    Box {
                        StartupHost()
                        when (AppStartup.state) {
                            StartupState.Error -> Button(onClick = {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        check(getSharedPreferences("hub_settings", MODE_PRIVATE).edit()
                                            .putString("library_sort", "TITLE").commit())
                                    }
                                    AppStartup.start(applicationContext)
                                }
                            }) { Text("Repair test storage") }
                            is StartupState.Ready -> {
                                var verified by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    check(MediaLibrary.entries.size == 500)
                                    check(HistoryLog.entries.isEmpty()) // browser data is deferred
                                    check((AppStartup.state as StartupState.Ready).settings.librarySort == HubLibrarySort.TITLE)
                                    MediaLibrary.restoreAsync(applicationContext)
                                    LibraryFolders.restoreAsync(applicationContext)
                                    check(MediaLibrary.entries.size == 500)
                                    check(withContext(Dispatchers.IO) { TabSessionStore.load(applicationContext) }!!.first.size == 30)
                                    verified = true
                                }
                                if (verified) Button(onClick = { recreate() }) { Text("Startup retry verified; recreate") }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
