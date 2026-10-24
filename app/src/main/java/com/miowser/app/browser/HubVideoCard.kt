package com.miowser.app.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.LibraryFolders
import com.miowser.app.ui.MiowserMenuDivider
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.MiowserDropdownMenu
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

/** Reusable 16:9 media card for the Library and future downloaded-media surfaces. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HubVideoCard(
    title: String,
    sourceLabel: String,
    positionMs: Long,
    durationMs: Long,
    listMode: Boolean = false,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    centerArtwork: ImageBitmap? = null,
    actionIcon: ImageVector = Icons.Rounded.PlayCircle,
    actionContentDescription: String? = null,
    statusLabel: String? = null,
    qualityLabel: String? = null,
    onClick: (() -> Unit)? = null,
    menuOnLongPress: Boolean = false,
    menuContentDescription: String = "Video options",
    menuActionLabel: String? = null,
    onMenuAction: (() -> Unit)? = null,
    secondaryMenuActionLabel: String? = null,
    onSecondaryMenuAction: (() -> Unit)? = null,
    folderMenuOnSelect: ((String?) -> Unit)? = null,
    currentFolderId: String? = null,
    tertiaryMenuActionLabel: String? = null,
    onTertiaryMenuAction: (() -> Unit)? = null,
    deferArtwork: () -> Boolean = { false }
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var folderMenuFolderId by remember { mutableStateOf<String?>(null) }
    fun dismissMenu() {
        menuExpanded = false
        folderMenuOpen = false
        folderMenuFolderId = null
    }
    val progress = mediaWatchProgress(positionMs, durationMs)
    val context = LocalContext.current
    var thumbnail by remember(thumbnailUrl) { mutableStateOf(LibraryThumbnailStore.peek(thumbnailUrl)) }
    val scrolling = deferArtwork()
    LaunchedEffect(thumbnailUrl, scrolling) {
        val uri = thumbnailUrl
        if (!scrolling && thumbnail == null && uri != null) {
            delay(120) // Cancel cards that only pass through the viewport during a fling.
            thumbnail = LibraryThumbnailStore.loadVisible(context.applicationContext, uri)
        }
    }
    val placeholderStart = MaterialTheme.colorScheme.primaryContainer
    val placeholderEnd = MaterialTheme.colorScheme.tertiaryContainer
    val placeholderBrush = remember(placeholderStart, placeholderEnd) {
        Brush.linearGradient(listOf(placeholderStart, placeholderEnd))
    }
    val menuContent: @Composable () -> Unit = {
                if ((menuActionLabel != null && onMenuAction != null) || folderMenuOnSelect != null ||
                    (secondaryMenuActionLabel != null && onSecondaryMenuAction != null) ||
                    (tertiaryMenuActionLabel != null && onTertiaryMenuAction != null)) {
                    Box {
                        if (!menuOnLongPress && !listMode) {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = menuContentDescription)
                            }
                        }
                        MiowserDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { dismissMenu() }
                        ) {
                            if (folderMenuOpen && folderMenuOnSelect != null) {
                                val parent = LibraryFolders.entries.firstOrNull { it.id == folderMenuFolderId }
                                MiowserMenuItem(
                                    text = { Text(parent?.name ?: "Move to folder", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                                    onClick = {
                                        if (parent == null) folderMenuOpen = false
                                        else folderMenuFolderId = parent.parentId
                                    }
                                )
                                MiowserMenuDivider()
                                if (parent == null) {
                                    MiowserMenuItem(
                                        text = { Text("Unfiled") },
                                        selected = currentFolderId == null,
                                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                        onClick = { dismissMenu(); folderMenuOnSelect(null) }
                                    )
                                } else {
                                    MiowserMenuItem(
                                        text = { Text("Move here") },
                                        selected = currentFolderId == parent.id,
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                        onClick = { dismissMenu(); folderMenuOnSelect(parent.id) }
                                    )
                                }
                                LibraryFolders.entries.filter { it.parentId == folderMenuFolderId }.forEach { folder ->
                                    val hasChildren = LibraryFolders.entries.any { it.parentId == folder.id }
                                    MiowserMenuItem(
                                        text = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                                        selected = currentFolderId == folder.id,
                                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                        trailingIcon = if (hasChildren) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }) else null,
                                        onClick = {
                                            if (hasChildren) folderMenuFolderId = folder.id
                                            else { dismissMenu(); folderMenuOnSelect(folder.id) }
                                        }
                                    )
                                }
                            } else {
                                if (tertiaryMenuActionLabel != null && onTertiaryMenuAction != null) {
                                    MiowserMenuItem(
                                        text = { Text(tertiaryMenuActionLabel) },
                                        leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                                        onClick = {
                                            dismissMenu()
                                            onTertiaryMenuAction()
                                        }
                                    )
                                }
                                if (folderMenuOnSelect != null) {
                                    MiowserMenuItem(
                                        text = { Text("Move to folder") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                        trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                                        onClick = { folderMenuFolderId = null; folderMenuOpen = true }
                                    )
                                }
                                if (secondaryMenuActionLabel != null && onSecondaryMenuAction != null) {
                                    MiowserMenuItem(
                                        text = { Text(secondaryMenuActionLabel) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                        onClick = {
                                            dismissMenu()
                                            onSecondaryMenuAction()
                                        }
                                    )
                                }
                                if (menuActionLabel != null && onMenuAction != null) {
                                    if (folderMenuOnSelect != null || secondaryMenuActionLabel != null || tertiaryMenuActionLabel != null) {
                                        MiowserMenuDivider()
                                    }
                                    MiowserMenuItem(
                                        text = { Text(menuActionLabel) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                        onClick = {
                                            dismissMenu()
                                            onMenuAction()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
    }
    val artwork: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .then(if (listMode) Modifier.width(136.dp).height(84.dp) else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .clip(if (listMode) RoundedCornerShape(14.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .then(if (thumbnail == null) Modifier.background(placeholderBrush) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f))
                    )
                }
                if (centerArtwork != null) {
                    Image(
                        bitmap = centerArtwork,
                        contentDescription = "App icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp))
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Icon(
                            actionIcon,
                            contentDescription = actionContentDescription,
                            modifier = Modifier.padding(7.dp).size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        actionIcon,
                        contentDescription = actionContentDescription,
                        tint = if (thumbnail != null) {
                            androidx.compose.ui.graphics.Color.White
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(44.dp)
                    )
                }
                if (durationMs > 0L) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Text(
                            formatMediaDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                qualityLabel?.let { label ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                statusLabel?.let { label ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                if (!listMode) Box(modifier = Modifier.align(Alignment.TopEnd)) { menuContent() }
            }
    }

    val details: @Composable () -> Unit = {
            if (durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                    )
                    if (progress > 0f) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
    }

    val cardContent: @Composable () -> Unit = {
        if (listMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                artwork()
                Box(Modifier.weight(1f)) { details() }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = menuContentDescription)
                    }
                    menuContent()
                }
            }
        } else {
            Column { artwork(); details() }
        }
    }

    if (menuOnLongPress) {
        Card(
            modifier = modifier
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = menuContentDescription
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) { cardContent() }
    } else if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) { cardContent() }
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) { cardContent() }
    }
}

internal fun mediaWatchProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

internal fun formatMediaDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
