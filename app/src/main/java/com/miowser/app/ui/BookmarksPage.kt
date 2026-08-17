package com.miowser.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
            Text(
                text = "No bookmarks yet — tap the star in the address bar to add one.",
                modifier = modifier.padding(24.dp)
            )
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
