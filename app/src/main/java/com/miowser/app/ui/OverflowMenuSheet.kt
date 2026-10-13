package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.ceil
import kotlin.math.floor

/** Every full-page destination in the app. */
enum class Screen { HUB, DETECTED, DOWNLOADS, HISTORY, ADBLOCK, BOOKMARKS, SETTINGS, CLEAR_DATA, SITE_PERMISSIONS, TABS }

private const val MIN_MENU_COLUMNS = 5
private val MENU_TILE_HEIGHT = 76.dp
private val MENU_ROW_SPACING = 4.dp

private data class MenuPresentation(
    val label: String,
    val icon: ImageVector,
    val screen: Screen?
)

/**
 * The browser hamburger menu. Its plain icon tiles use at least five columns and add columns as
 * width permits. Edit mode turns the grid into a persistent drag-and-drop arrangement surface;
 * normal mode opens destinations or shares the current webpage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowMenuSheet(
    activeDownloadCount: Int,
    detectedCount: Int,
    shareEnabled: Boolean,
    onShare: () -> Unit,
    onSelect: (Screen) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val orderedActions = remember {
        mutableStateListOf<OverflowMenuAction>().apply {
            addAll(OverflowMenuOrderStore.load(context))
        }
    }
    var editMode by remember { mutableStateOf(false) }
    var draggedAction by remember { mutableStateOf<OverflowMenuAction?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOriginIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }

    fun clearDrag() {
        draggedAction = null
        dragOffset = Offset.Zero
        dragOriginIndex = -1
        dragTargetIndex = -1
    }

    fun finishDrag() {
        val action = draggedAction
        val from = action?.let(orderedActions::indexOf) ?: -1
        val to = dragTargetIndex.coerceIn(0, orderedActions.lastIndex)
        if (from >= 0 && to >= 0 && from != to) {
            val moved = orderedActions.removeAt(from)
            orderedActions.add(to, moved)
            OverflowMenuOrderStore.save(context, orderedActions)
        }
        clearDrag()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MiowserBottomSheetShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (editMode) "Arrange menu" else "Browser menu",
                    style = MaterialTheme.typography.titleMedium
                )
                if (editMode) {
                    Text(
                        text = "Drag tiles into your preferred order",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = {
                if (editMode) clearDrag()
                editMode = !editMode
            }) {
                Icon(
                    imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = null
                )
                Text(if (editMode) "Done" else "Edit", modifier = Modifier.padding(start = 4.dp))
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 68dp is compact enough for five tiles on an ordinary portrait phone. Wider phones
            // and landscape layouts gain columns rather than stretching five oversized tiles.
            val columns = floor(maxWidth.value / 68f).toInt().coerceAtLeast(MIN_MENU_COLUMNS)
            val rows = ceil(orderedActions.size / columns.toFloat()).toInt().coerceAtLeast(1)
            val cellWidthPx = constraints.maxWidth.toFloat() / columns
            val rowPitchPx = with(density) { (MENU_TILE_HEIGHT + MENU_ROW_SPACING).toPx() }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MENU_TILE_HEIGHT * rows + MENU_ROW_SPACING * (rows - 1)),
                verticalArrangement = Arrangement.spacedBy(MENU_ROW_SPACING)
            ) {
                itemsIndexed(orderedActions, key = { _, action -> action.name }) { index, action ->
                    val presentation = action.presentation()
                    val enabled = action != OverflowMenuAction.SHARE || shareEnabled
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
                            if (action == OverflowMenuAction.SHARE) {
                                if (enabled) {
                                    onDismiss()
                                    onShare()
                                }
                            } else {
                                presentation.screen?.let(onSelect)
                            }
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
                            val projectedX = (originColumn + 0.5f) * cellWidthPx + nextOffset.x
                            val projectedY = (originRow + 0.5f) * rowPitchPx + nextOffset.y
                            val targetColumn = floor(projectedX / cellWidthPx).toInt()
                                .coerceIn(0, columns - 1)
                            val targetRow = floor(projectedY / rowPitchPx).toInt().coerceAtLeast(0)
                            dragTargetIndex = (targetRow * columns + targetColumn)
                                .coerceIn(0, orderedActions.lastIndex)
                        },
                        onDragEnd = ::finishDrag,
                        onDragCancel = ::clearDrag
                    )
                }
            }
        }
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
        .height(MENU_TILE_HEIGHT)
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
        modifier = interactionModifier.padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun OverflowMenuAction.presentation(): MenuPresentation = when (this) {
    OverflowMenuAction.HUB -> MenuPresentation("Hub", Icons.Rounded.Home, Screen.HUB)
    OverflowMenuAction.ADBLOCK -> MenuPresentation("Ad blocker", Icons.Rounded.Shield, Screen.ADBLOCK)
    OverflowMenuAction.DOWNLOADS -> MenuPresentation("Downloads", Icons.Rounded.Download, Screen.DOWNLOADS)
    OverflowMenuAction.HISTORY -> MenuPresentation("History", Icons.Rounded.History, Screen.HISTORY)
    OverflowMenuAction.DETECTED -> MenuPresentation("Media", Icons.Rounded.VideoLibrary, Screen.DETECTED)
    OverflowMenuAction.SETTINGS -> MenuPresentation("Settings", Icons.Rounded.Settings, Screen.SETTINGS)
    OverflowMenuAction.SHARE -> MenuPresentation("Share", Icons.Rounded.Share, null)
}
