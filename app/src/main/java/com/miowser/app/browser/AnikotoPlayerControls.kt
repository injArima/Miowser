package com.miowser.app.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AnikotoPlayerChoices(
    kind: String,
    session: AnikotoPlaybackSession,
    servers: List<AnikotoServer>,
    onLanguage: (String) -> Unit,
    onServer: (AnikotoServer) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (kind == "servers") "Choose server" else "Choose version") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (kind == "servers") {
                    val choices = servers.filter { it.language == session.language }
                    if (choices.isEmpty()) Text("No servers are available for this version yet.")
                    choices.forEach { server ->
                        TextButton(onClick = { onServer(server) }, modifier = Modifier.fillMaxWidth()) {
                            Text(server.label + if (server.id == session.serverId) " · Current" else "")
                        }
                    }
                } else {
                    listOf("sub" to "Sub", "dub" to "Dub").forEach { (value, label) ->
                        TextButton(onClick = { onLanguage(value) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label + if (value == session.language) " · Current" else "")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
internal fun AnikotoSwitchOverlay(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOtherServer: () -> Unit,
    onOpenSource: () -> Unit
) {
    if (!loading && error == null) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (loading) {
                CircularProgressIndicator(color = Color.White)
                Text("Loading episode…", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            } else {
                Text(error.orEmpty(), color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    OutlinedButton(onClick = onOtherServer) { Text("Other server") }
                }
                TextButton(onClick = onOpenSource) { Text("Open source page") }
            }
        }
    }
}
