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
import com.miowser.app.LauncherShortcutRequest
import com.miowser.app.ui.MiowserLoadingIndicator
import com.miowser.app.ui.theme.UiSpacing

@Composable
fun StartupHost(
    shortcutRequest: LauncherShortcutRequest? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppStartup.start(context) }
    val state = AppStartup.state
    ReportDrawnWhen { AppStartup.state is StartupState.Ready }
    when (state) {
        is StartupState.Ready -> BrowserScreen(
            initialHubSettings = state.settings,
            shortcutRequest = shortcutRequest,
            onShortcutHandled = onShortcutHandled
        )
        else -> StartupLoading(
            error = state == StartupState.Error,
            openingBrowser = shortcutRequest != null,
            onRetry = { AppStartup.start(context) }
        )
    }
}

@Composable
fun StartupLoading(error: Boolean, openingBrowser: Boolean = false, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .testTag(if (error) "startup_error" else "startup_loading").safeDrawingPadding().padding(UiSpacing.xxl),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Miowser", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(UiSpacing.xxl))
        if (error) {
            Text("Couldn’t load your saved data.")
            Button(onClick = onRetry) { Text("Retry") }
        } else {
            MiowserLoadingIndicator(contained = true, description = "Starting Miowser")
            Spacer(Modifier.height(UiSpacing.l))
            Text(if (openingBrowser) "Opening browser…" else "Loading your Library…")
        }
    }
}
