package com.miowser.app.startup

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.miowser.app.browser.BrowserScreen

@Composable
fun StartupHost() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppStartup.start(context) }
    val state = AppStartup.state
    ReportDrawnWhen { AppStartup.state is StartupState.Ready }
    when (state) {
        is StartupState.Ready -> BrowserScreen(initialHubSettings = state.settings)
        else -> StartupLoading(error = state == StartupState.Error, onRetry = { AppStartup.start(context) })
    }
}

@Composable
fun StartupLoading(error: Boolean, onRetry: () -> Unit, browser: Boolean = false) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .testTag(if (error) "startup_error" else "startup_loading").safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Miowser", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        if (error) {
            Text(if (browser) "Couldn’t open the browser. Your saved data is still here." else "Couldn’t load your saved data.")
            Button(onClick = onRetry) { Text("Retry") }
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(if (browser) "Opening browser…" else "Loading your Library…")
        }
    }
}
