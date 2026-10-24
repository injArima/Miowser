package com.miowser.app.bookmarks

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "bookmarks"
private const val KEY_BOOKMARKS = "bookmarks"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'
private const val FIELD_COUNT = 3

data class Bookmark(
    val url: String,
    val title: String,
    val addedAt: Long
)

/** Delimited-text codec — pure logic, no Android dependency; same shape as DownloadCodec/HistoryCodec. */
object BookmarkCodec {
    fun encode(bookmarks: List<Bookmark>): String =
        bookmarks.joinToString(RECORD_DELIMITER.toString()) { b ->
            listOf(sanitize(b.url), sanitize(b.title), b.addedAt.toString())
                .joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<Bookmark> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_DELIMITER).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): Bookmark? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != FIELD_COUNT) return null
        return runCatching {
            Bookmark(url = parts[0], title = parts[1], addedAt = parts[2].toLong())
        }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/** Thin SharedPreferences wrapper around [BookmarkCodec]. */
object BookmarkStore {
    fun save(context: Context, bookmarks: List<Bookmark>): java.util.concurrent.CompletableFuture<Boolean> {
        Persistence.requireOwner()
        val appContext = context.applicationContext
        val snapshot = bookmarks.toList()
        return Persistence.submitSnapshot("bookmarks") {
            val saved = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_BOOKMARKS, BookmarkCodec.encode(snapshot)).commit()
            saved
        }
    }

    fun load(context: Context): List<Bookmark> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BOOKMARKS, null)
            ?: return emptyList()
        return BookmarkCodec.decode(raw)
    }
}
