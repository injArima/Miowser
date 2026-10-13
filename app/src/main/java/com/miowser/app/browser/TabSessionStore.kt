package com.miowser.app.browser

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "browser_session"
private const val KEY_TABS = "tabs"
private const val KEY_ACTIVE_INDEX = "active_index"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'

/** One restored tab: its last-known URL, and its opener's position in the *same saved list* (-1 = none). */
data class SavedTab(
    val url: String,
    val openerIndex: Int,
    val thumbnailKey: String = "",
    val title: String = "",
    val lastActiveAt: Long = 0L
)

/** Resolve relationships before engine creation; reordered sessions may refer to a later record. */
internal fun restoredOpenerIds(records: List<SavedTab>, ids: List<Long>): List<Long?> {
    require(records.size == ids.size)
    return records.mapIndexed { index, tab ->
        tab.openerIndex.takeIf { it != index }?.let(ids::getOrNull)
    }
}

/**
 * Encodes/decodes the tab list to a flat string — pure logic, no Android dependency, so it's
 * directly unit-testable. Deliberately URL-level, not full WebView.saveState()/back-stack
 * restoration: scroll position, form state, and in-page navigation history within a tab are
 * lost, only "which tabs were open, in what order, showing what page" survives. A plain
 * tab/newline-delimited string rather than a JSON dependency: the record shape is simple enough
 * (five fields, no nesting) that a real parser would be overhead for no real benefit, and URLs
 * practically never contain raw tab/newline characters.
 */
object TabSessionCodec {
    fun encode(tabs: List<SavedTab>): String =
        tabs.joinToString(RECORD_DELIMITER.toString()) {
            listOf(
                it.openerIndex.toString(), it.lastActiveAt.toString(), sanitize(it.thumbnailKey),
                sanitize(it.title), sanitize(it.url)
            ).joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<SavedTab> =
        raw.split(RECORD_DELIMITER).mapNotNull { line ->
            val parts = line.split(FIELD_DELIMITER, limit = 5)
            if (parts.size < 2) return@mapNotNull null
            val isCurrent = parts.size == 5
            val key = when {
                isCurrent -> parts[2]
                parts.size == 3 -> parts[1]
                else -> ""
            }
            val url = when {
                isCurrent -> parts[4]
                parts.size == 3 -> parts[2]
                else -> parts[1]
            }
            if (url.isBlank()) return@mapNotNull null
            SavedTab(
                url = url,
                openerIndex = parts[0].toIntOrNull() ?: -1,
                thumbnailKey = key,
                title = if (isCurrent) parts[3] else "",
                lastActiveAt = if (isCurrent) parts[1].toLongOrNull() ?: 0L else 0L
            )
        }

    private fun sanitize(url: String): String =
        url.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/** Thin SharedPreferences wrapper around [TabSessionCodec] — the only Context-dependent part. */
object TabSessionStore {
    @Volatile private var latestSnapshot: Pair<List<SavedTab>, Int>? = null

    fun snapshot(tabs: List<Tab>, activeTabId: Long?): Pair<List<SavedTab>, Int> {
        Persistence.requireOwner()
        val regularTabs = tabs.filterNot(Tab::isPrivate)
        val idToIndex = regularTabs.withIndex().associate { (index, tab) -> tab.id to index }
        val savedTabs = regularTabs.map { tab ->
            val openerIndex = tab.openerTabId?.let { idToIndex[it] } ?: -1
            SavedTab(
                url = tab.url.ifBlank { tab.addressBarText.text },
                openerIndex = openerIndex,
                thumbnailKey = tab.thumbnailKey,
                title = tab.title,
                lastActiveAt = tab.lastActiveAt
            )
        }
        val activeIndex = regularTabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

        return savedTabs to activeIndex
    }

    fun save(context: Context, tabs: List<Tab>, activeTabId: Long?) =
        saveSnapshot(context, snapshot(tabs, activeTabId))

    fun saveSnapshot(context: Context, snapshot: Pair<List<SavedTab>, Int>) {
        Persistence.requireOwner()
        val savedTabs = snapshot.first.toList()
        val activeIndex = snapshot.second.coerceIn(0, (savedTabs.size - 1).coerceAtLeast(0))
        latestSnapshot = savedTabs to activeIndex
        val appContext = context.applicationContext
        Persistence.submitSnapshot("tabs") {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TABS, TabSessionCodec.encode(savedTabs))
                .putInt(KEY_ACTIVE_INDEX, activeIndex).commit()
        }
    }

    /** Returns the saved tabs plus which index was active, or null if there's no saved session. */
    fun load(context: Context): Pair<List<SavedTab>, Int>? {
        latestSnapshot?.let { return it.takeIf { saved -> saved.first.isNotEmpty() } }
        val persisted = readPersisted(context)
        // A main-owner save may have arrived while preferences were being read.
        latestSnapshot?.let { return it.takeIf { saved -> saved.first.isNotEmpty() } }
        return persisted
    }

    private fun readPersisted(context: Context): Pair<List<SavedTab>, Int>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TABS, null)
        if (raw.isNullOrBlank()) return null

        val tabs = TabSessionCodec.decode(raw)
        if (tabs.isEmpty()) return null

        val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0).coerceIn(0, tabs.size - 1)
        return tabs to activeIndex
    }
}
