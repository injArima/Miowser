package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape

/** Site-specific choices; server entries are never passed to the native player as media URLs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnikotoEpisodeSheet(
    catalog: AnikotoCatalog?,
    onRefresh: () -> Unit,
    onOpenEpisode: (AnikotoEpisode) -> Unit,
    onOpenSeason: (AnikotoSeason) -> Unit,
    onSelectServer: (AnikotoServer) -> Unit,
    onDismiss: () -> Unit
) {
    var language by remember { mutableStateOf("") }
    val visibleEpisodes = remember(catalog, language) {
        catalog?.episodes.orEmpty().filter {
            language.isEmpty() || (language == "sub" && it.hasSub) ||
                (language == "dub" && it.hasDub)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(catalog?.showTitle?.ifBlank { "Episodes" } ?: "Episodes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp))
            Text("Episodes, audio versions, and servers from this page",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All", "sub" to "Sub", "dub" to "Dub").forEach { (value, label) ->
                    FilterChip(selected = language == value, onClick = { language = value },
                        label = { Text(label) })
                }
            }
            if (catalog == null || catalog.episodes.isEmpty()) {
                Text("The episode list is still loading on this page.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRefresh) { Text("Check again") }
            } else {
                Text("${visibleEpisodes.size} episodes", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (catalog.servers.isNotEmpty()) {
                        item(key = "servers-heading") {
                            Text("Current episode · servers", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                        items(catalog.servers, key = { "server-${it.language}-${it.id}-${it.episodeId}" }) { server ->
                            Surface(onClick = { onSelectServer(server) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (server.active) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Text("${server.language.uppercase()} · ${server.label}",
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (catalog.seasons.isNotEmpty()) {
                        item(key = "seasons-heading") {
                            Text("Other seasons", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        }
                        items(catalog.seasons, key = { "season-${it.url}" }) { season ->
                            Surface(onClick = { onOpenSeason(season) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Text(season.title.ifBlank { season.url },
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item(key = "episodes-heading") {
                        Text("Episodes", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                    items(visibleEpisodes, key = { it.url }) { episode ->
                        Surface(onClick = { onOpenEpisode(episode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (episode.active) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Episode ${episode.number}" +
                                    if (episode.active) " · Current" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                if (episode.title.isNotBlank()) Text(episode.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(if (episode.hasSub) "Sub" else null,
                                    if (episode.hasDub) "Dub" else null).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
