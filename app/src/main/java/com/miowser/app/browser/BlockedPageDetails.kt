package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.AdBlockFilters
import com.miowser.app.adblock.BlockedPageInfo

@Composable
internal fun BlockedPageDetails(info: BlockedPageInfo, modifier: Modifier = Modifier) {
    var expanded by remember(info.url, info.rule) { mutableStateOf(false) }
    val currentLists = AdBlockFilters.subscriptions.associateBy { it.id }
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Blocking lists" + if (info.sources.isEmpty()) "" else " (${info.sources.size})")
                Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null)
            }
        }
        if (expanded) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.sources.isEmpty()) Text("Rule source unavailable")
                info.sources.forEach { source ->
                    val current = currentLists[source.id]
                    Column {
                        Text(current?.name ?: source.name, style = MaterialTheme.typography.titleSmall)
                        (current?.url ?: source.url)?.let { url ->
                            Text(url, style = MaterialTheme.typography.bodySmall)
                        }
                        if (source.url != null && (current == null || !current.enabled)) {
                            Text("Still in installed rules; list update pending.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text("Matching rule", style = MaterialTheme.typography.labelLarge)
                Text(info.rule, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
