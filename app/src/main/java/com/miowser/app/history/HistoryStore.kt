package com.miowser.app.history

import android.content.Context

private const val PREFS_NAME = "history"
private const val KEY_ENTRIES = "entries"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'
private const val FIELD_COUNT = 4

/** One visited page. [visitCount] and [lastVisitedAt] bump on a repeat visit rather than adding a new row (see HistoryLog). */
data class HistoryEntry(
    val url: String,
    val title: String,
    val lastVisitedAt: Long,
    val visitCount: Int
)

/** Delimited-text codec — pure logic, no Android dependency; same shape as DownloadCodec/TabSessionCodec. */
object HistoryCodec {
    fun encode(entries: List<HistoryEntry>): String =
        entries.joinToString(RECORD_DELIMITER.toString()) { e ->
            listOf(sanitize(e.url), sanitize(e.title), e.lastVisitedAt.toString(), e.visitCount.toString())
                .joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<HistoryEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_DELIMITER).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): HistoryEntry? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != FIELD_COUNT) return null
        return runCatching {
            HistoryEntry(
                url = parts[0],
                title = parts[1],
                lastVisitedAt = parts[2].toLong(),
                visitCount = parts[3].toInt()
            )
        }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/** Thin SharedPreferences wrapper around [HistoryCodec]. */
object HistoryStore {
    fun save(context: Context, entries: List<HistoryEntry>) {
        // commit(), not apply(): apply() queues the disk write and returns immediately, so a
        // process death (crash, OS low-memory kill, force-stop) shortly after can lose it before
        // it ever lands — confirmed as a real, reproducible bug for the equivalent DownloadStore
        // write (see its own doc comment). commit()'s synchronous block is a non-issue here: none
        // of these save() calls are on a per-frame/per-keystroke hot path.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, HistoryCodec.encode(entries))
            .commit()
    }

    fun load(context: Context): List<HistoryEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null)
            ?: return emptyList()
        return HistoryCodec.decode(raw)
    }
}
