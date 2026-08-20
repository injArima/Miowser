package com.miowser.app.browser

import android.content.Context

private const val PREFS_NAME = "browser_session"
private const val KEY_TABS = "tabs"
private const val KEY_ACTIVE_INDEX = "active_index"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'

/** One restored tab: its last-known URL, and its opener's position in the *same saved list* (-1 = none). */
data class SavedTab(val url: String, val openerIndex: Int)

/**
 * Encodes/decodes the tab list to a flat string — pure logic, no Android dependency, so it's
 * directly unit-testable. Deliberately URL-level, not full WebView.saveState()/back-stack
 * restoration: scroll position, form state, and in-page navigation history within a tab are
 * lost, only "which tabs were open, in what order, showing what page" survives. A plain
 * tab/newline-delimited string rather than a JSON dependency: the record shape is simple enough
 * (two fields, no nesting) that a real parser would be overhead for no real benefit, and URLs
 * practically never contain raw tab/newline characters.
 */
object TabSessionCodec {
    fun encode(tabs: List<SavedTab>): String =
        tabs.joinToString(RECORD_DELIMITER.toString()) { "${it.openerIndex}$FIELD_DELIMITER${sanitize(it.url)}" }

    fun decode(raw: String): List<SavedTab> =
        raw.split(RECORD_DELIMITER).mapNotNull { line ->
            val parts = line.split(FIELD_DELIMITER, limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return@mapNotNull null
            SavedTab(url = parts[1], openerIndex = parts[0].toIntOrNull() ?: -1)
        }

    private fun sanitize(url: String): String =
        url.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/** Thin SharedPreferences wrapper around [TabSessionCodec] — the only Context-dependent part. */
object TabSessionStore {
    fun save(context: Context, tabs: List<Tab>, activeTabId: Long?) {
        val idToIndex = tabs.withIndex().associate { (index, tab) -> tab.id to index }
        val savedTabs = tabs.map { tab ->
            val openerIndex = tab.openerTabId?.let { idToIndex[it] } ?: -1
            SavedTab(url = tab.url.ifBlank { tab.addressBarText.text }, openerIndex = openerIndex)
        }
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }

        // commit(), not apply() — see DownloadStore's matching doc comment for why: apply()'s
        // async write can be lost to a process death before it lands. This one's called from
        // ON_PAUSE specifically to survive the app being killed shortly after, so the guarantee
        // matters here more than almost anywhere else in the app.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TABS, TabSessionCodec.encode(savedTabs))
            .putInt(KEY_ACTIVE_INDEX, activeIndex)
            .commit()
    }

    /** Returns the saved tabs plus which index was active, or null if there's no saved session. */
    fun load(context: Context): Pair<List<SavedTab>, Int>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TABS, null)
        if (raw.isNullOrBlank()) return null

        val tabs = TabSessionCodec.decode(raw)
        if (tabs.isEmpty()) return null

        val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0).coerceIn(0, tabs.size - 1)
        return tabs to activeIndex
    }
}
