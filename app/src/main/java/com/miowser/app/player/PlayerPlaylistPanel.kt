package com.miowser.app.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.library.LibraryEntry

/** A right-side queue over the video; playback remains visible and running beneath it. */
@Composable
internal fun PlayerPlaylistPanel(
    entries: List<LibraryEntry>,
    currentKey: String,
    onSelect: (LibraryEntry) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester
) {
    val currentIndex = entries.indexOfFirst { it.identityKey == currentKey }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val width = minOf(maxWidth, minOf(420.dp, maxOf(240.dp,
            maxWidth * if (landscape) 0.38f else 0.88f)))
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight()
                .then(modifier).playerPanelPointerShield(),
            color = Color(0xFF17191D).copy(alpha = 0.97f),
            contentColor = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Playlist", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("${currentIndex + 1} of ${entries.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.focusRequester(initialFocus)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close playlist")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous video")
                    }
                    IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next video")
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(entries, key = { _, entry -> entry.identityKey }) { index, entry ->
                        val selected = entry.identityKey == currentKey
                        Surface(
                            onClick = { if (!selected) onSelect(entry) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.07f),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.65f))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = Color.White)
                                    Text(if (selected) "Now playing · ${entry.sourceLabel}" else entry.sourceLabel,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
