package com.miowser.app.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reusable 16:9 media card for the Library and future downloaded-media surfaces. */
@Composable
fun HubVideoCard(
    title: String,
    sourceLabel: String,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    centerArtwork: ImageBitmap? = null,
    actionIcon: ImageVector = Icons.Rounded.PlayCircle,
    actionContentDescription: String? = null,
    statusLabel: String? = null,
    onClick: (() -> Unit)? = null,
    menuContentDescription: String = "Video options",
    menuActionLabel: String? = null,
    onMenuAction: (() -> Unit)? = null,
    secondaryMenuActionLabel: String? = null,
    onSecondaryMenuAction: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progress = mediaWatchProgress(positionMs, durationMs)
    val context = LocalContext.current
    val thumbnail by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = LibraryThumbnailStore.peek(thumbnailUrl),
        thumbnailUrl
    ) {
        value = thumbnailUrl?.let { uri ->
            withContext(Dispatchers.IO) { LibraryThumbnailStore.load(context.applicationContext, uri) }
        }
    }
    val cardContent: @Composable () -> Unit = {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
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
                if ((menuActionLabel != null && onMenuAction != null) ||
                    (secondaryMenuActionLabel != null && onSecondaryMenuAction != null)) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = menuContentDescription)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (secondaryMenuActionLabel != null && onSecondaryMenuAction != null) {
                                DropdownMenuItem(
                                    text = { Text(secondaryMenuActionLabel) },
                                    onClick = {
                                        menuExpanded = false
                                        onSecondaryMenuAction()
                                    }
                                )
                            }
                            if (menuActionLabel != null && onMenuAction != null) {
                                DropdownMenuItem(
                                    text = { Text(menuActionLabel) },
                                    onClick = {
                                        menuExpanded = false
                                        onMenuAction()
                                    }
                                )
                            }
                        }
                    }
                }
            }

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
    }

    if (onClick != null) {
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
