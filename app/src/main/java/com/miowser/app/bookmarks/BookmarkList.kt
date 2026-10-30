package com.miowser.app.bookmarks

import android.content.Context
import com.miowser.app.persistence.Persistence
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The bookmark list — mirrors DownloadQueue/HistoryLog's singleton-object pattern. */
object BookmarkList {
    private val mutableRecords = mutableStateListOf<Bookmark>()
    val bookmarks: List<Bookmark> get() = mutableRecords
    var revision by mutableIntStateOf(0)
        private set

    @Volatile private var restored = false

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BookmarkStore.load(context) }
        if (restored) return
        mutableRecords.addAll(records)
        revision++
        restored = true
    }

    fun restore(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        mutableRecords.addAll(BookmarkStore.load(context))
        revision++
        restored = true
    }

    fun isBookmarked(url: String): Boolean = mutableRecords.any { it.url == url }

    /**
     * Adds a bookmark for [url] if it isn't already one, or removes it if it is — the address
     * bar's star button. Returns whether the page is now bookmarked (true = just added, false =
     * just removed), so callers can react only to the add — e.g. a "Bookmarked" confirmation
     * snackbar makes sense on add, not on remove.
     */
    fun toggle(context: Context, url: String, title: String): Boolean {
        Persistence.requireOwner()
        val existingIndex = mutableRecords.indexOfFirst { it.url == url }
        val nowBookmarked = existingIndex < 0
        if (existingIndex >= 0) {
            mutableRecords.removeAt(existingIndex)
        } else {
            mutableRecords.add(0, Bookmark(url = url, title = title.ifBlank { url }, addedAt = System.currentTimeMillis()))
        }
        revision++
        persist(context)
        return nowBookmarked
    }

    fun remove(context: Context, bookmark: Bookmark) {
        Persistence.requireOwner()
        if (mutableRecords.remove(bookmark)) {
            revision++
            persist(context)
        }
    }

    private fun persist(context: Context) {
        Persistence.requireOwner()
        BookmarkStore.save(context, mutableRecords)
    }
}
