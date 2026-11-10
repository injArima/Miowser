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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.flow.collectLatest

data class PlayerPlaylistPage(val start: Int, val entries: List<LibraryEntry>) {
    operator fun get(index: Int): LibraryEntry? = entries.getOrNull(index - start)
}

data class PagedPlayerPlaylist(
    val total: Int,
    val activeIndex: Int,
    val initialPage: PlayerPlaylistPage,
    val load: suspend (Int) -> PlayerPlaylistPage,
    val select: (Int) -> Unit
)

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
    initialFocus: FocusRequester,
    paged: PagedPlayerPlaylist? = null
) {
    val currentIndex = paged?.activeIndex ?: entries.indexOfFirst { it.identityKey == currentKey }
    val total = paged?.total ?: entries.size
    val list = rememberLazyListState(initialFirstVisibleItemIndex = paged?.activeIndex ?: 0)
    var page by remember(paged) { mutableStateOf(paged?.initialPage) }
    LaunchedEffect(paged, list) {
        if (paged == null) return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex }.collectLatest { index ->
            if (page?.get(index) == null || (index + 4 < total && page?.get(index + 4) == null)) {
                page = paged.load(index)
            }
        }
    }
    fun item(index: Int): LibraryEntry? = if (paged == null) entries.getOrNull(index) else page?.get(index)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val width = minOf(maxWidth, minOf(420.dp, maxOf(240.dp,
            maxWidth * if (landscape) 0.38f else 0.88f)))
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight()
                .then(modifier).playerPanelPointerShield(),
            color = PlayerUiColors.sidePanel.copy(alpha = 0.97f),
            contentColor = PlayerUiColors.content,
            shadowElevation = UiElevation.floating
        ) {
            Column(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(start = UiSpacing.m, end = UiSpacing.m, top = UiSpacing.s, bottom = UiSpacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Playlist", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, color = PlayerUiColors.content)
                        Text("${currentIndex + 1} of $total",
                            style = MaterialTheme.typography.bodySmall,
                            color = PlayerUiColors.content.copy(alpha = 0.7f))
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
                LazyColumn(state = list, modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
                    items(total, key = { index -> if (paged == null) entries[index].identityKey else index }) { index ->
                        val entry = item(index)
                        if (entry == null) {
                            Box(Modifier.fillMaxWidth().height(54.dp).background(PlayerUiColors.content.copy(alpha = 0.05f)))
                            return@items
                        }
                        val selected = index == currentIndex
                        Surface(
                            onClick = {
                                if (!selected) {
                                    if (paged == null) onSelect(entry) else {
                                        onDismiss()
                                        paged.select(index)
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else PlayerUiColors.content.copy(alpha = 0.07f),
                            contentColor = PlayerUiColors.content
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = UiSpacing.m, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.compact)
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                                    color = PlayerUiColors.content.copy(alpha = 0.65f))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = PlayerUiColors.content)
                                    Text(if (selected) "Now playing · ${entry.sourceLabel}" else entry.sourceLabel,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PlayerUiColors.content.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
