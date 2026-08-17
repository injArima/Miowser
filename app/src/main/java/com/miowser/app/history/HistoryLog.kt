package com.miowser.app.history

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

// A generous but real cap — without one, a long-lived install's history would grow the
// SharedPreferences-backed record forever. 2000 entries covers months of normal browsing
// while staying cheap to encode/decode on every visit.
private const val MAX_ENTRIES = 2000

/**
 * The browsing-history log — mirrors DownloadQueue's singleton-object pattern. Visits are
 * deduped by exact URL: a repeat visit bumps [HistoryEntry.visitCount] and moves the entry to
 * the front with a fresh timestamp and (possibly updated) title, rather than growing the list
 * with one row per visit the way a raw visit log would.
 */
object HistoryLog {
    val entries = mutableStateListOf<HistoryEntry>()

    @Volatile private var restored = false

    fun restore(context: Context) {
        if (restored) return
        restored = true
        entries.addAll(HistoryStore.load(context))
    }

    fun recordVisit(context: Context, url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        val existingIndex = entries.indexOfFirst { it.url == url }
        val visitCount = if (existingIndex >= 0) entries[existingIndex].visitCount + 1 else 1
        if (existingIndex >= 0) entries.removeAt(existingIndex)
        entries.add(0, HistoryEntry(url = url, title = title.ifBlank { url }, lastVisitedAt = System.currentTimeMillis(), visitCount = visitCount))
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.size - 1)
        persist(context)
    }

    fun remove(context: Context, entry: HistoryEntry) {
        entries.remove(entry)
        persist(context)
    }

    fun clear(context: Context) {
        entries.clear()
        persist(context)
    }

    private fun persist(context: Context) {
        HistoryStore.save(context, entries)
    }
}
