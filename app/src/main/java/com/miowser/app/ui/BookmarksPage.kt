package com.miowser.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.miowser.app.bookmarks.Bookmark

@Composable
fun BookmarksPage(
    bookmarks: List<Bookmark>,
    onOpenUrl: (String) -> Unit,
    onRemove: (Bookmark) -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Bookmarks", onBack = onBack) { modifier ->
        if (bookmarks.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    title = "No bookmarks yet",
                    description = "Tap the star in the address bar to save a page.",
                    icon = Icons.Rounded.Bookmarks
                )
            }
            return@SimplePageScaffold
        }
        LazyColumn(modifier = modifier) {
            items(bookmarks, key = { it.url }) { bookmark ->
                LibraryRow(
                    title = bookmark.title,
                    subtitle = bookmark.url,
                    onClick = { onOpenUrl(bookmark.url) },
                    onRemove = { onRemove(bookmark) }
                )
            }
        }
    }
}
