package com.miowser.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miowser.app.browser.SavedSitePermission
import com.miowser.app.browser.SiteDecision

private fun permissionLabel(resource: String): String = when {
    resource.endsWith("VIDEO_CAPTURE") -> "Camera"
    resource.endsWith("AUDIO_CAPTURE") -> "Microphone"
    resource == "geolocation" -> "Location"
    else -> resource.substringAfterLast('.')
}

@Composable
fun SitePermissionsPage(
    decisions: List<SavedSitePermission>,
    onForget: (SavedSitePermission) -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Site permissions", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier) {
            if (decisions.isEmpty()) item {
                Text("No remembered site decisions", modifier = Modifier.padding(20.dp))
            }
            items(decisions, key = { "${it.origin}|${it.resource}" }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.origin, style = MaterialTheme.typography.bodyLarge)
                        Text("${permissionLabel(item.resource)}: ${if (item.decision == SiteDecision.ALLOW) "Allowed" else "Blocked"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { onForget(item) },
                        modifier = Modifier.semantics {
                            contentDescription = "Forget ${permissionLabel(item.resource)} decision for ${item.origin}"
                        }
                    ) { Text("Forget") }
                }
                }
            }
        }
    }
}
