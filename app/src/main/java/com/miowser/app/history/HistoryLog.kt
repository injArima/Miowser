package com.miowser.app.history

import android.content.Context
import com.miowser.app.persistence.Persistence
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    private val mutableRecords = mutableStateListOf<HistoryEntry>()
    val entries: List<HistoryEntry> get() = mutableRecords
    var revision by mutableIntStateOf(0)
        private set

    @Volatile private var restored = false

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HistoryStore.load(context) }
        if (restored) return
        mutableRecords.addAll(records)
        revision++
        restored = true
    }

    fun restore(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        mutableRecords.addAll(HistoryStore.load(context))
        revision++
        restored = true
    }

    fun recordVisit(context: Context, url: String, title: String) {
        Persistence.requireOwner()
        if (url.isBlank() || url == "about:blank") return
        val existingIndex = mutableRecords.indexOfFirst { it.url == url }
        val visitCount = if (existingIndex >= 0) mutableRecords[existingIndex].visitCount + 1 else 1
        if (existingIndex >= 0) mutableRecords.removeAt(existingIndex)
        mutableRecords.add(0, HistoryEntry(url = url, title = title.ifBlank { url }, lastVisitedAt = System.currentTimeMillis(), visitCount = visitCount))
        while (mutableRecords.size > MAX_ENTRIES) mutableRecords.removeAt(mutableRecords.size - 1)
        revision++
        persist(context)
    }

    fun remove(context: Context, entry: HistoryEntry) {
        Persistence.requireOwner()
        if (mutableRecords.remove(entry)) {
            revision++
            persist(context)
        }
    }

    /** Bulk-remove (e.g. everything from one date on HistoryPage's group-delete) with a single persist, not one per entry. */
    fun removeAll(context: Context, toRemove: Collection<HistoryEntry>) {
        Persistence.requireOwner()
        if (mutableRecords.removeAll(toRemove.toSet())) {
            revision++
            persist(context)
        }
    }

    fun clear(context: Context) {
        Persistence.requireOwner()
        if (mutableRecords.isEmpty()) return
        mutableRecords.clear()
        revision++
        persist(context)
    }

    private fun persist(context: Context) {
        Persistence.requireOwner()
        HistoryStore.save(context, mutableRecords)
    }
}
