package com.miowser.app.browser
import com.miowser.app.ui.theme.LocalMotionPolicy
import androidx.compose.ui.platform.testTag

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.MiowserMenuItem
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TabsPage(
    tabs: List<Tab>,
    inactiveTabs: List<InactiveTabRecord>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseTabs: (Set<Long>, Boolean) -> Unit,
    onRestoreInactive: (InactiveTabRecord) -> Unit,
    onDeleteInactive: (InactiveTabRecord) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBack: () -> Unit
) {
    val activeIsPrivate = tabs.firstOrNull { it.id == activeTabId }?.isPrivate == true
    var section by remember(activeTabId) { mutableIntStateOf(if (activeIsPrivate) 1 else 0) }
    var showingInactive by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val visibleTabs = tabs.filter { it.isPrivate == (section == 1) }
    val activeInSection = visibleTabs.any { it.id == activeTabId }
    // The grid is created at the selected card's index before its first measure. A remembered
    // saveable grid can restore an old index despite a different initial index, and the old
    // LaunchedEffect then visibly scrolled from that position after the page appeared.
    val initialActiveIndex = visibleTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
    val gridState = remember(section, activeTabId) { LazyGridState(initialActiveIndex, 0) }

    BackHandler(enabled = showingInactive) { showingInactive = false }

    SimplePageScaffold(
        title = if (showingInactive) "Inactive tabs" else "",
        onBack = { if (showingInactive) showingInactive = false else onBack() },
        actions = {
            if (!showingInactive) {
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Tab actions")
                    }
                    MiowserDropdownMenu(actionsExpanded, { actionsExpanded = false }) {
                        MiowserMenuItem(text = { Text("Close current tab") }, enabled = activeInSection,
                            onClick = {
                                actionsExpanded = false
                                activeTabId?.let(onCloseTab)
                            })
                        MiowserMenuItem(text = { Text("Close other tabs") },
                            enabled = activeInSection && visibleTabs.size > 1,
                            onClick = {
                                actionsExpanded = false
                                onCloseTabs(visibleTabs.map { it.id }.filter { it != activeTabId }.toSet(), false)
                            })
                        MiowserMenuItem(text = { Text("Close all tabs") }, enabled = tabs.isNotEmpty(),
                            onClick = {
                                actionsExpanded = false
                                onCloseTabs(tabs.map { it.id }.toSet(), false)
                            })
                        MiowserMenuItem(text = { Text("Close all tabs and exit browser") },
                            enabled = tabs.isNotEmpty(), onClick = {
                                actionsExpanded = false
                                onCloseTabs(tabs.map { it.id }.toSet(), true)
                            })
                    }
                }
                IconToggleButton(
                    checked = section == 0,
                    onCheckedChange = { section = 0 },
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(
                        if (section == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = "Browsing tabs",
                        tint = if (section == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconToggleButton(
                    checked = section == 1,
                    onCheckedChange = { section = 1 },
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(14.dp)).background(
                        if (section == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    IncognitoIcon(
                        tint = if (section == 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "Private tabs" }
                    )
                }
            }
        }
    ) { modifier ->
        if (showingInactive) {
            InactiveTabList(inactiveTabs, onRestoreInactive, onDeleteInactive, modifier)
            return@SimplePageScaffold
        }

        Box(modifier = modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.fillMaxSize().testTag("tabs_grid"),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(visibleTabs, key = { it.id }, contentType = { "tab-preview" }) { tab ->
                        SwipeCloseTabCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            gridState = gridState,
                            onClick = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) }
                        )
                    }
                }
            }
          }
          FloatingActionButton(
              onClick = { if (section == 0) onNewTab() else onNewPrivateTab() },
              modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                  .testTag("new_tab_fab"),
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ) {
              Icon(Icons.Rounded.Add,
                  contentDescription = if (section == 0) "New browsing tab" else "New private tab")
          }
        }
    }
}

@Composable
private fun SwipeCloseTabCard(
    tab: Tab,
    isActive: Boolean,
    gridState: LazyGridState,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val motion = rememberUpdatedState(LocalMotionPolicy.current)
    val swipe = remember(tab.id) { TabSwipeMotion() }
    val scope = rememberCoroutineScope()
    var settleJob by remember(tab.id) { mutableStateOf<Job?>(null) }
    val latestOnClose = rememberUpdatedState(onClose)
    var cardWidthPx by remember(tab.id) { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged {
                val previousWidth = cardWidthPx
                cardWidthPx = it.width
                if (previousWidth != 0 && previousWidth != it.width) {
                    settleJob?.cancel()
                    val token = swipe.takeControl()
                    settleJob = scope.launch {
                        swipe.settle(token, 0f, it.width.toFloat(), 0f, motion.value)
                    }
                }
            }
            .clip(MaterialTheme.shapes.medium)
            .pointerInput(tab.id, gridState, cardWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (gridState.isScrollInProgress) return@awaitEachGesture
                    settleJob?.cancel()
                    val token = swipe.takeControl()
                    val startOffset = swipe.offset
                    val velocity = VelocityTracker().also { it.addPosition(down.uptimeMillis, down.position) }
                    var dx = 0f
                    var dy = 0f
                    var dragging = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.id != down.id && it.pressed }) break
                            val change = event.changes
                                .firstOrNull { it.id == down.id } ?: break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                released = change.previousPressed && !change.isConsumed
                                if (dragging) change.consume()
                                break
                            }
                            if (change.isConsumed) break
                            val delta = change.positionChange()
                            dx += delta.x
                            dy += delta.y
                            if (!dragging) {
                                dragging = startsTabCloseSwipe(dx, dy, viewConfiguration.touchSlop)
                                // Once vertical scrolling wins, leave the entire gesture to the grid.
                                if (!dragging && abs(dy) > viewConfiguration.touchSlop) break
                            }
                            if (dragging) {
                                change.consume()
                                swipe.drag(token, startOffset + dx, cardWidthPx.toFloat())
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        released = false
                        throw cancelled
                    } finally {
                        val width = cardWidthPx.toFloat()
                        val close = dragging && released &&
                            commitsTabCloseSwipe(swipe.offset, dy, width, 72.dp.toPx())
                        val destination = if (close) { if (swipe.offset > 0f) width else -width } else 0f
                        if ((dragging || swipe.offset != 0f) && swipe.owns(token)) settleJob = scope.launch {
                            swipe.settle(token, destination, width,
                                if (released) velocity.calculateVelocity().x else 0f, motion.value,
                                if (close) ({ latestOnClose.value() }) else null)
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Icon(Icons.Rounded.Close, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Box(Modifier.offset { IntOffset(swipe.offset.roundToInt(), 0) }) {
            TabPreviewCard(tab = tab, isActive = isActive, onClick = onClick, onClose = {
                settleJob?.cancel()
                swipe.closeImmediately { latestOnClose.value() }
            })
        }
    }
}

@Composable
private fun InactiveTabsCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("$count inactive ${if (count == 1) "tab" else "tabs"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "View inactive tabs")
        }
    }
}

@Composable
private fun IncognitoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 24f
        val hat = Path().apply {
            moveTo(7f * u, 10f * u)
            lineTo(8.5f * u, 3f * u)
            quadraticTo(12f * u, 2f * u, 15.5f * u, 3f * u)
            lineTo(17f * u, 10f * u)
            close()
        }
        drawPath(hat, tint)
        drawLine(tint, Offset(3f * u, 10.5f * u), Offset(21f * u, 10.5f * u),
            strokeWidth = 2.5f * u, cap = StrokeCap.Round)
        drawCircle(tint, radius = 3.2f * u, center = Offset(8f * u, 17f * u),
            style = Stroke(width = 2f * u))
        drawCircle(tint, radius = 3.2f * u, center = Offset(16f * u, 17f * u),
            style = Stroke(width = 2f * u))
        drawLine(tint, Offset(11.2f * u, 17f * u), Offset(12.8f * u, 17f * u),
            strokeWidth = 2f * u)
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
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onRestore(record) },
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
        modifier = Modifier.fillMaxWidth().testTag("tab_card")
            .let { if (isActive) it.border(2.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clip(shape)
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
