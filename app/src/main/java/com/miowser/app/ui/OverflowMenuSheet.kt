package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import kotlin.math.floor
import kotlinx.coroutines.launch

/** Every full-page destination in the app. */
enum class Screen {
    HUB, DETECTED, DOWNLOADS, HISTORY, ADBLOCK, BOOKMARKS, SETTINGS, CLEAR_DATA,
    SITE_PERMISSIONS, CRASH_DIAGNOSTICS, TABS
}

private val MENU_TILE_WIDTH = 48.dp
private val MENU_TILE_HEIGHT = 48.dp
private val MENU_ICON_SPACING = 10.dp

private data class MenuPresentation(
    val label: String,
    val icon: ImageVector,
    val screen: Screen?
)

/** Reorderable shortcuts first, page actions after an upward swipe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowMenuSheet(
    activeDownloadCount: Int,
    detectedCount: Int,
    shareEnabled: Boolean,
    pageActionsEnabled: Boolean,
    onShare: () -> Unit,
    onFind: () -> Unit,
    onZoom: () -> Unit,
    onTextSize: () -> Unit,
    onClearCache: () -> Unit,
    onClearCookiesAndSiteData: () -> Unit,
    desktopMode: Boolean,
    onToggleDesktopMode: () -> Unit,
    onPrint: () -> Unit,
    onSelect: (Screen) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val motion = LocalMotionPolicy.current
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val orderedActions = remember {
        mutableStateListOf<OverflowMenuAction>().apply {
            addAll(OverflowMenuOrderStore.load(context))
        }
    }
    var editMode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val expandedContentState = remember { MutableTransitionState(false) }
    expandedContentState.targetState = expanded
    var showThemePicker by remember { mutableStateOf(false) }
    val shortcuts = orderedActions.filter { it != OverflowMenuAction.SHARE }
    var draggedAction by remember { mutableStateOf<OverflowMenuAction?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOriginIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    val swipeThresholdPx = with(density) { 24.dp.toPx() }
    LaunchedEffect(expanded) {
        if (!expanded && scrollState.value != 0) {
            scrollState.animateScrollTo(0, motion.spec(MotionTokens.controlsExit))
        }
    }
    val collapseOnOverscroll = remember(scrollState, swipeThresholdPx) {
        object : NestedScrollConnection {
            private var downwardDrag = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || scrollState.value > 0 || available.y <= 0f) {
                    downwardDrag = 0f
                    return Offset.Zero
                }
                downwardDrag += available.y
                if (downwardDrag < swipeThresholdPx) return Offset.Zero
                downwardDrag = 0f
                expanded = false
                return available
            }
        }
    }

    fun clearDrag() {
        draggedAction = null
        dragOffset = Offset.Zero
        dragOriginIndex = -1
        dragTargetIndex = -1
    }

    fun finishDrag() {
        val action = draggedAction
        val from = action?.let(shortcuts::indexOf) ?: -1
        val to = dragTargetIndex.coerceIn(0, shortcuts.lastIndex)
        if (from >= 0 && to >= 0 && from != to) {
            val reordered = shortcuts.toMutableList()
            reordered.add(to, reordered.removeAt(from))
            orderedActions.clear()
            orderedActions.addAll(reordered + OverflowMenuAction.SHARE)
            OverflowMenuOrderStore.save(context, orderedActions)
        }
        clearDrag()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(Modifier
                .semantics { contentDescription = if (expanded) "Show quick access" else "Show more actions" }
                .pointerInput(expanded) {
                    var drag = 0f
                    var handled = false
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f; handled = false },
                        onVerticalDrag = { change, amount ->
                            if (handled) return@detectVerticalDragGestures
                            drag += amount
                            if (drag < -swipeThresholdPx && !expanded) {
                                expanded = true
                                handled = true
                                change.consume()
                            } else if (drag > swipeThresholdPx) {
                                if (expanded) expanded = false
                                else scope.launch { sheetState.hide(); onDismiss() }
                                handled = true
                                change.consume()
                            }
                        }
                    )
                }
                .clickable { expanded = !expanded }
            ) { CompactSheetDragHandle() }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MiowserBottomSheetShape
    ) {
      val contentModifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.88f)
          .pointerInput(expanded, editMode) {
              if (!expanded && !editMode) {
                  var drag = 0f
                  var handled = false
                  detectVerticalDragGestures(
                      onDragStart = { drag = 0f; handled = false },
                      onVerticalDrag = { change, amount ->
                          if (handled) return@detectVerticalDragGestures
                          drag += amount
                          if (drag < -swipeThresholdPx) {
                              expanded = true
                              handled = true
                              change.consume()
                          } else if (drag > swipeThresholdPx) {
                              scope.launch { sheetState.hide(); onDismiss() }
                              handled = true
                              change.consume()
                          }
                      }
                  )
              }
          }
      Column((if (expandedContentState.currentState || expandedContentState.targetState)
          contentModifier.nestedScroll(collapseOnOverscroll).verticalScroll(scrollState)
          else contentModifier).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MenuSectionTitle("QUICK ACCESS") }
                IconButton(onClick = {
                    if (editMode) clearDrag()
                    editMode = !editMode
                }) {
                    Icon(
                        imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = if (editMode) "Done arranging shortcuts" else "Edit shortcuts",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Fit as many 48 dp touch targets as the width permits, with at least 10 dp between them.
            val columns = ((maxWidth.value + MENU_ICON_SPACING.value) /
                (MENU_TILE_WIDTH.value + MENU_ICON_SPACING.value)).toInt()
                .coerceIn(1, shortcuts.size)
            val rows = (shortcuts.size + columns - 1) / columns
            val gapPx = with(density) { MENU_ICON_SPACING.toPx() }
            val cellWidthPx = (constraints.maxWidth - gapPx * (columns - 1)) / columns
            val cellPitchPx = cellWidthPx + gapPx
            val rowPitchPx = with(density) { (MENU_TILE_HEIGHT + MENU_ICON_SPACING).toPx() }
            val tileHeightPx = with(density) { MENU_TILE_HEIGHT.toPx() }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MENU_TILE_HEIGHT * rows + MENU_ICON_SPACING * (rows - 1).coerceAtLeast(0)),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(MENU_ICON_SPACING),
                verticalArrangement = Arrangement.spacedBy(MENU_ICON_SPACING)
            ) {
                itemsIndexed(shortcuts, key = { _, action -> action.name }) { index, action ->
                    val presentation = action.presentation()
                    val enabled = action != OverflowMenuAction.ZOOM || pageActionsEnabled
                    val isDragged = draggedAction == action
                    val isTarget = draggedAction != null && dragTargetIndex == index

                    MenuTile(
                        icon = presentation.icon,
                        label = presentation.label,
                        badgeCount = when (action) {
                            OverflowMenuAction.DOWNLOADS -> activeDownloadCount
                            OverflowMenuAction.DETECTED -> detectedCount
                            else -> 0
                        },
                        enabled = enabled,
                        editMode = editMode,
                        isDragged = isDragged,
                        isDropTarget = isTarget,
                        dragOffset = if (isDragged) dragOffset else Offset.Zero,
                        onClick = {
                            if (action == OverflowMenuAction.ZOOM) {
                                onDismiss()
                                onZoom()
                            } else presentation.screen?.let(onSelect)
                        },
                        onDragStart = {
                            draggedAction = action
                            dragOffset = Offset.Zero
                            dragOriginIndex = index
                            dragTargetIndex = index
                        },
                        onDrag = { amount ->
                            if (draggedAction != action || dragOriginIndex < 0) return@MenuTile
                            val nextOffset = dragOffset + amount
                            dragOffset = nextOffset

                            val originColumn = dragOriginIndex % columns
                            val originRow = dragOriginIndex / columns
                            val projectedX = originColumn * cellPitchPx + cellWidthPx / 2f + nextOffset.x
                            val projectedY = originRow * rowPitchPx + tileHeightPx / 2f + nextOffset.y
                            val targetColumn = floor((projectedX + gapPx / 2f) / cellPitchPx).toInt()
                                .coerceIn(0, columns - 1)
                            val targetRow = floor((projectedY + gapPx / 2f) / rowPitchPx)
                                .toInt().coerceAtLeast(0)
                            dragTargetIndex = (targetRow * columns + targetColumn)
                                .coerceIn(0, shortcuts.lastIndex)
                        },
                        onDragEnd = ::finishDrag,
                        onDragCancel = ::clearDrag
                    )
                }
            }
        }
          }
        }
        AnimatedVisibility(
            visibleState = expandedContentState,
            enter = expandVertically(animationSpec = motion.spec(MotionTokens.controlsEnter),
                expandFrom = Alignment.Top) +
                fadeIn(animationSpec = motion.spec(MotionTokens.controlsEnter)),
            exit = shrinkVertically(animationSpec = motion.spec(MotionTokens.controlsExit),
                shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = motion.spec(MotionTokens.controlsExit))
        ) {
          Column {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    MenuSectionTitle("PAGE ACTIONS")
                    Spacer(Modifier.height(8.dp))
                    MenuActionRow(Icons.Rounded.Share, "Share page", "Send or copy the page link",
                        shareEnabled, onClick = { onDismiss(); onShare() })
                    MenuActionRow(Icons.Rounded.Search, "Find in page", "Search text on this page",
                        pageActionsEnabled, onClick = { onDismiss(); onFind() })
                    MenuActionRow(Icons.Rounded.TextFields, "Text size", "Adjust text on this page",
                        pageActionsEnabled, onClick = { onDismiss(); onTextSize() })
                    MenuActionRow(Icons.Rounded.DeleteSweep, "Clear browser cache", "Cached files for all websites",
                        true, onClick = { onDismiss(); onClearCache() })
                    MenuActionRow(Icons.Rounded.Cookie, "Clear cookies and site data", "All websites; may sign you out",
                        true, onClick = { onDismiss(); onClearCookiesAndSiteData() })
                    MenuActionRow(Icons.Rounded.Web, "Request desktop website",
                        if (desktopMode) "Desktop site on" else "Show the desktop layout",
                        pageActionsEnabled, onClick = onToggleDesktopMode,
                        toggleChecked = desktopMode)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
                MenuSmallAction(Icons.Rounded.Palette, "Theme", AppThemePreference.mode.label, true,
                    onClick = { showThemePicker = true }, modifier = Modifier.weight(1f))
                MenuSmallAction(Icons.Rounded.PictureAsPdf, "Print / PDF", "Save or print",
                    pageActionsEnabled, onClick = { onDismiss(); onPrint() }, modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
    if (showThemePicker) {
        AlertDialog(onDismissRequest = { showThemePicker = false },
            title = { Text("Theme") },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                AppThemePreference.set(context, mode)
                                showThemePicker = false
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label,
                                modifier = Modifier.weight(1f))
                            if (AppThemePreference.mode == mode) {
                                Icon(Icons.Rounded.Check, "Selected",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("Close") }
            })
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    enabled: Boolean,
    editMode: Boolean,
    isDragged: Boolean,
    isDropTarget: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val contentColor = when {
        !enabled && !editMode -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDragged || isDropTarget -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var interactionModifier = Modifier
        .fillMaxWidth()
        .height(MENU_TILE_HEIGHT)
        .semantics { contentDescription = label }
        .zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            translationX = dragOffset.x
            translationY = dragOffset.y
            if (isDragged) {
                scaleX = 1.06f
                scaleY = 1.06f
                alpha = 0.92f
            }
        }
        .clip(RoundedCornerShape(16.dp))

    interactionModifier = if (editMode) {
        interactionModifier.pointerInput(label) {
            detectDragGestures(
                onDragStart = { onDragStart() },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount)
                }
            )
        }
    } else {
        interactionModifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = interactionModifier.padding(4.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) {
            if (badgeCount > 0) {
                BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MenuSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MenuActionRow(icon: ImageVector, title: String, detail: String,
                          enabled: Boolean, onClick: () -> Unit = {},
                          toggleChecked: Boolean? = null) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = if (toggleChecked == null) Role.Button else Role.Switch,
            onClick = onClick)
        .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (toggleChecked != null) {
            Switch(checked = toggleChecked, onCheckedChange = { onClick() }, enabled = enabled)
        } else if (enabled) Icon(Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MenuSmallAction(icon: ImageVector, title: String, detail: String,
                            enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    Surface(modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun OverflowMenuAction.presentation(): MenuPresentation = when (this) {
    OverflowMenuAction.HUB -> MenuPresentation("Hub", Icons.Rounded.Home, Screen.HUB)
    OverflowMenuAction.ADBLOCK -> MenuPresentation("Shield", Icons.Rounded.Shield, Screen.ADBLOCK)
    OverflowMenuAction.DOWNLOADS -> MenuPresentation("Downloads", Icons.Rounded.Download, Screen.DOWNLOADS)
    OverflowMenuAction.HISTORY -> MenuPresentation("History", Icons.Rounded.History, Screen.HISTORY)
    OverflowMenuAction.DETECTED -> MenuPresentation("Media", Icons.Rounded.VideoLibrary, Screen.DETECTED)
    OverflowMenuAction.SETTINGS -> MenuPresentation("Settings", Icons.Rounded.Settings, Screen.SETTINGS)
    OverflowMenuAction.ZOOM -> MenuPresentation("Zoom", Icons.Rounded.ZoomIn, null)
    OverflowMenuAction.SHARE -> MenuPresentation("Share", Icons.Rounded.Share, null)
}
