package com.miowser.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.miowser.app.browser.SpeedDialSiteMetadataStore
import com.miowser.app.history.HistoryEntry
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiCorners
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.HistoryRowTokens

// A bulk delete pending confirmation — everything, or just one date's entries. Single-row
// deletion (LibraryRow's own cross button) never goes through this: only operations that erase
// more than one entry at once are risky enough to ask first.
private sealed class PendingBulkDelete {
    data class WholeDay(val date: LocalDate, val entries: List<HistoryEntry>) : PendingBulkDelete()
    data object Everything : PendingBulkDelete()
}

@Composable
fun HistoryPage(
    entries: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onRemoveEntries: (List<HistoryEntry>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PendingBulkDelete?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun closeSearch() {
        searchQuery = ""
        searchActive = false
    }

    BackHandler(enabled = searchActive, onBack = ::closeSearch)

    SimplePageScaffold(
        title = "History",
        onBack = { if (searchActive) closeSearch() else onBack() },
        titleContent = {
            if (searchActive) {
                HistorySearchField(query = searchQuery, onQueryChange = { searchQuery = it })
            } else {
                Text("History", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            if (!searchActive && entries.isNotEmpty()) {
                IconButton(onClick = { searchActive = true }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search history")
                }
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "History options")
                    }
                    MiowserDropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        MiowserMenuItem(
                            text = { Text("Clear all history") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                pendingDelete = PendingBulkDelete.Everything
                            }
                        )
                    }
                }
            }
        }
    ) { modifier ->
        if (entries.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    title = "No history yet",
                    description = "Pages you visit will appear here.",
                    icon = Icons.Rounded.History
                )
            }
            return@SimplePageScaffold
        }
        val normalizedQuery = searchQuery.trim()
        val visibleEntries = if (normalizedQuery.isEmpty()) entries else entries.filter { entry ->
            entry.title.contains(normalizedQuery, ignoreCase = true) ||
                entry.url.contains(normalizedQuery, ignoreCase = true)
        }
        if (visibleEntries.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    title = "No matching history",
                    description = "Try another page title or URL.",
                    icon = Icons.Rounded.Search
                )
            }
            return@SimplePageScaffold
        }

        // HistoryLog owns a mutableStateList and keeps the same list identity as its contents
        // change. Recompute this inexpensive grouping so additions and removals never go stale.
        val grouped = visibleEntries.sortedByDescending { it.lastVisitedAt }.groupBy { it.visitedOnDate() }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            grouped.entries.forEachIndexed { index, (date, dayEntries) ->
                item(key = "header-$date") {
                    DateHeader(
                        date = date,
                        isFirst = index == 0,
                        onDelete = { pendingDelete = PendingBulkDelete.WholeDay(date, dayEntries) }
                    )
                }
                itemsIndexed(dayEntries, key = { _, entry -> entry.url }) { itemIndex, entry ->
                    HistoryListItem(
                        entry = entry,
                        position = segmentedPosition(itemIndex, dayEntries.lastIndex),
                        onClick = { onOpenUrl(entry.url) },
                        onRemove = { onRemove(entry) }
                    )
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        val message: String
        val action: () -> Unit
        when (pending) {
            is PendingBulkDelete.WholeDay -> {
                val count = pending.entries.size
                message = "Delete $count ${if (count == 1) "entry" else "entries"} from ${formatDateHeader(pending.date)}?"
                action = { onRemoveEntries(pending.entries) }
            }
            PendingBulkDelete.Everything -> {
                message = "Delete all history? This can't be undone."
                action = onClear
            }
        }
        MiowserConfirmationDialog(
            title = "Delete history",
            message = message,
            onDismiss = { pendingDelete = null },
            actions = listOf(
                ConfirmationAction("Cancel", { pendingDelete = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Delete", { action(); pendingDelete = null }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(start = UiSpacing.m, end = UiSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(UiIconSize.menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(9.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search titles or URLs",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(UiControlSize.compact)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear history search")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun HistoryListItem(
    entry: HistoryEntry,
    position: SegmentedPosition,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = when (position) {
        SegmentedPosition.ONLY -> UiCorners.r24
        SegmentedPosition.FIRST -> RoundedCornerShape(
            topStart = HistoryRowTokens.outerCorner,
            topEnd = HistoryRowTokens.outerCorner,
            bottomStart = HistoryRowTokens.innerCorner,
            bottomEnd = HistoryRowTokens.innerCorner
        )
        SegmentedPosition.MIDDLE -> RoundedCornerShape(HistoryRowTokens.innerCorner)
        SegmentedPosition.LAST -> RoundedCornerShape(
            topStart = HistoryRowTokens.innerCorner,
            topEnd = HistoryRowTokens.innerCorner,
            bottomStart = HistoryRowTokens.outerCorner,
            bottomEnd = HistoryRowTokens.outerCorner
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (position == SegmentedPosition.LAST || position == SegmentedPosition.ONLY) 0.dp else HistoryRowTokens.segmentGap)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HistoryRowTokens.minHeight)
                .padding(start = UiSpacing.m, top = UiSpacing.compact, end = UiSpacing.xs, bottom = UiSpacing.compact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistorySiteIcon(entry = entry)
            Spacer(Modifier.width(UiSpacing.m))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                Text(
                    text = entry.title.ifBlank { displayUrl(entry.url) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayUrl(entry.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "  •  ${entry.visitedAtTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove ${entry.title.ifBlank { entry.url }} from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(UiIconSize.menu)
                )
            }
        }
    }
}

@Composable
private fun HistorySiteIcon(entry: HistoryEntry) {
    val context = LocalContext.current
    val favicon by produceState<Bitmap?>(initialValue = null, key1 = entry.url) {
        value = SpeedDialSiteMetadataStore.cachedIcon(context, entry.url)
    }
    val host = remember(entry.url) { hostName(entry.url) }
    val paletteIndex = remember(host) { (host.hashCode() and Int.MAX_VALUE) % 3 }
    val container = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (paletteIndex) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.size(HistoryRowTokens.siteIconSize),
        shape = HistoryRowTokens.siteIconShape,
        color = container,
        border = BorderStroke(1.dp, content.copy(alpha = 0.24f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (favicon != null) {
                Image(
                    bitmap = favicon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(UiIconSize.featured),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = host.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, isFirst: Boolean, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (isFirst) 8.dp else 20.dp)
    ) {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = UiSpacing.s),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(HistoryRowTokens.headerHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relativeDateLabel(date),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = date.format(SHORT_DATE_FORMAT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete ${formatDateHeader(date)}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(UiIconSize.menu)
                )
            }
        }
    }
}

private enum class SegmentedPosition { ONLY, FIRST, MIDDLE, LAST }

private fun segmentedPosition(index: Int, lastIndex: Int): SegmentedPosition = when {
    lastIndex == 0 -> SegmentedPosition.ONLY
    index == 0 -> SegmentedPosition.FIRST
    index == lastIndex -> SegmentedPosition.LAST
    else -> SegmentedPosition.MIDDLE
}

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val SHORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun relativeDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.dayOfWeek.name
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(HEADER_DATE_FORMAT)
    }
}

private fun HistoryEntry.visitedOnDate(): LocalDate =
    Instant.ofEpochMilli(lastVisitedAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun HistoryEntry.visitedAtTime(): String =
    Instant.ofEpochMilli(lastVisitedAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private fun displayUrl(url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")
    .removeSuffix("/")

private fun hostName(url: String): String = runCatching {
    URI(url).host?.removePrefix("www.")
}.getOrNull().orEmpty().ifBlank { displayUrl(url) }
