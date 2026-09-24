package com.miowser.app.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ExpressiveEmptyState
import java.text.DateFormat
import java.util.Date

@Composable
fun TabsPage(
    tabs: List<Tab>,
    inactiveTabs: List<InactiveTabRecord>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onRestoreInactive: (InactiveTabRecord) -> Unit,
    onDeleteInactive: (InactiveTabRecord) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBack: () -> Unit
) {
    val activeIsPrivate = tabs.firstOrNull { it.id == activeTabId }?.isPrivate == true
    var section by remember(activeTabId) { mutableIntStateOf(if (activeIsPrivate) 1 else 0) }
    var showingInactive by remember { mutableStateOf(false) }
    val visibleTabs = tabs.filter { it.isPrivate == (section == 1) }
    // The grid is created at the selected card's index before its first measure. A remembered
    // saveable grid can restore an old index despite a different initial index, and the old
    // LaunchedEffect then visibly scrolled from that position after the page appeared.
    val initialActiveIndex = visibleTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
    val gridState = remember(section, activeTabId) { LazyGridState(initialActiveIndex, 0) }

    BackHandler(enabled = showingInactive) { showingInactive = false }

    SimplePageScaffold(
        title = if (showingInactive) "Inactive tabs" else "Tabs",
        onBack = { if (showingInactive) showingInactive = false else onBack() },
        actions = {
            if (!showingInactive) {
                IconButton(onClick = { if (section == 0) onNewTab() else onNewPrivateTab() }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = if (section == 0) "New browsing tab" else "New private tab"
                    )
                }
            }
        }
    ) { modifier ->
        if (showingInactive) {
            InactiveTabList(inactiveTabs, onRestoreInactive, onDeleteInactive, modifier)
            return@SimplePageScaffold
        }

        Column(modifier = modifier.fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                FilterChip(
                    selected = section == 0,
                    onClick = { section = 0 },
                    label = { Text("Browsing (${tabs.count { !it.isPrivate }})") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = section == 1,
                    onClick = { section = 1 },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                    label = { Text("Private (${tabs.count { it.isPrivate }})") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (section == 0 && inactiveTabs.isNotEmpty()) {
                InactiveTabsCard(
                    count = inactiveTabs.size,
                    onClick = { showingInactive = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (visibleTabs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ExpressiveEmptyState(
                        title = if (section == 1) "No private tabs" else "No browsing tabs",
                        description = "Tap + to open a tab in this category.",
                        icon = if (section == 1) Icons.Rounded.VisibilityOff else Icons.Rounded.Add
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(visibleTabs, key = { it.id }, contentType = { "tab-preview" }) { tab ->
                        TabPreviewCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onClick = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InactiveTabsCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("$count inactive ${if (count == 1) "tab" else "tabs"}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Unused or duplicate tabs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "View inactive tabs")
        }
    }
}

@Composable
private fun InactiveTabList(
    records: List<InactiveTabRecord>,
    onRestore: (InactiveTabRecord) -> Unit,
    onDelete: (InactiveTabRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExpressiveEmptyState(
                title = "No inactive tabs",
                description = "Tabs you have not used recently will appear here.",
                icon = Icons.Rounded.History
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listItems(records, key = { it.id }) { record ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onRestore(record) },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.title.ifBlank { record.url }, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(record.url, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${if (record.reason == InactiveTabReason.DUPLICATE) "Duplicate" else "Unused"} · ${formatInactiveDate(record.archivedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    IconButton(onClick = { onDelete(record) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete inactive tab")
                    }
                }
            }
        }
    }
}

private fun formatInactiveDate(time: Long): String = if (time <= 0L) "Archived" else
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

@Composable
private fun TabPreviewCard(tab: Tab, isActive: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    LaunchedEffect(tab.thumbnailKey, tab.thumbnail == null) { tab.restoreThumbnailIfMissing() }
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = Modifier.fillMaxWidth()
            .let { if (isActive) it.border(2.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val preview = tab.thumbnail?.takeUnless { it.isRecycled }
            if (preview != null) {
                val image = remember(preview) { preview.asImageBitmap() }
                Image(bitmap = image, contentDescription = "Preview of ${tab.title}",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else TabPreviewSkeleton(Modifier.fillMaxSize())
            if (tab.isPrivate) {
                Icon(
                    Icons.Rounded.VisibilityOff, contentDescription = "Private tab",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(bottomEnd = 10.dp))
                        .padding(7.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close ${tab.title}")
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(tab.title.ifBlank { "New tab" }, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!tab.isShowingHome && tab.url.isNotBlank()) {
                Text(tab.url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TabPreviewSkeleton(modifier: Modifier = Modifier) {
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Column(
        modifier = modifier.semantics { contentDescription = "No tab preview available" }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp).height(28.dp)
                .background(blockColor, RoundedCornerShape(8.dp)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.78f).height(9.dp)
                    .background(blockColor, RoundedCornerShape(5.dp)))
                Box(Modifier.fillMaxWidth(0.52f).height(8.dp)
                    .background(blockColor, RoundedCornerShape(4.dp)))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)
            .background(blockColor, RoundedCornerShape(12.dp)))
        Box(Modifier.fillMaxWidth(0.86f).height(10.dp)
            .background(blockColor, RoundedCornerShape(5.dp)))
        Box(Modifier.fillMaxWidth(0.62f).height(10.dp)
            .background(blockColor, RoundedCornerShape(5.dp)))
    }
}
