package com.miowser.app.bookmarks

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/** The bookmark list — mirrors DownloadQueue/HistoryLog's singleton-object pattern. */
object BookmarkList {
    val bookmarks = mutableStateListOf<Bookmark>()

    @Volatile private var restored = false

    fun restore(context: Context) {
        if (restored) return
        restored = true
        bookmarks.addAll(BookmarkStore.load(context))
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    /**
     * Adds a bookmark for [url] if it isn't already one, or removes it if it is — the address
     * bar's star button. Returns whether the page is now bookmarked (true = just added, false =
     * just removed), so callers can react only to the add — e.g. a "Bookmarked" confirmation
     * snackbar makes sense on add, not on remove.
     */
    fun toggle(context: Context, url: String, title: String): Boolean {
        val existingIndex = bookmarks.indexOfFirst { it.url == url }
        val nowBookmarked = existingIndex < 0
        if (existingIndex >= 0) {
            bookmarks.removeAt(existingIndex)
        } else {
            bookmarks.add(0, Bookmark(url = url, title = title.ifBlank { url }, addedAt = System.currentTimeMillis()))
        }
        persist(context)
        return nowBookmarked
    }

    fun remove(context: Context, bookmark: Bookmark) {
        bookmarks.remove(bookmark)
        persist(context)
    }

    private fun persist(context: Context) {
        BookmarkStore.save(context, bookmarks)
    }
}
