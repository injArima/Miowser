package com.miowser.app.browser

import android.content.Context

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
    fun save(context: Context, tabs: List<Tab>, activeTabId: Long?) {
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
