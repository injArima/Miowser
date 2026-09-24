package com.miowser.app.library

import android.content.Context
import com.miowser.app.grabber.MediaType

private const val PREFS_NAME = "media_library"
private const val KEY_ENTRIES = "entries"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'
private const val LEGACY_FIELD_COUNT = 11
private const val FIELD_COUNT = 14

enum class LibrarySourceKind { REMOTE_STREAM, LOCAL_FILE }

/**
 * One video opened by Miowser's rich player. Remote URLs and local content URIs share the same
 * shape so the eventual Hub grid can render and resume either without maintaining two libraries.
 *
 * Cookies are deliberately absent. They expire quickly and may contain long-lived account
 * credentials; the player handoff will retrieve fresh cookies from WebView's CookieManager when
 * a remote entry is reopened. The user agent is retained because it is not a credential and some
 * media servers require the same client identity used during detection.
 */
data class LibraryEntry(
    val mediaUri: String,
    val pageUrl: String?,
    val title: String,
    val sourceLabel: String,
    val sourceKind: LibrarySourceKind,
    val mediaType: MediaType?,
    val userAgent: String?,
    val thumbnailUrl: String?,
    val lastPlayedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
    val pageTitle: String? = null,
    val videoName: String? = null,
    val folderId: String? = null
) {
    val identityKey: String get() = "${sourceKind.name}:$mediaUri"
}

/** Pure delimited-text codec, matching the existing History/Bookmark/Download stores. */
object LibraryCodec {
    fun encode(entries: List<LibraryEntry>): String =
        entries.joinToString(RECORD_DELIMITER.toString()) { entry ->
            listOf(
                sanitize(entry.mediaUri),
                entry.pageUrl?.let(::sanitize) ?: "",
                sanitize(entry.title),
                sanitize(entry.sourceLabel),
                entry.sourceKind.name,
                entry.mediaType?.name ?: "",
                entry.userAgent?.let(::sanitize) ?: "",
                entry.thumbnailUrl?.let(::sanitize) ?: "",
                entry.lastPlayedAt.toString(),
                entry.positionMs.coerceAtLeast(0L).toString(),
                entry.durationMs.coerceAtLeast(0L).toString(),
                entry.pageTitle?.let(::sanitize) ?: "",
                entry.videoName?.let(::sanitize) ?: "",
                entry.folderId?.let(::sanitize) ?: ""
            ).joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<LibraryEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_DELIMITER).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): LibraryEntry? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size !in setOf(LEGACY_FIELD_COUNT, FIELD_COUNT) || parts[0].isBlank()) return null
        return runCatching {
            LibraryEntry(
                mediaUri = parts[0],
                pageUrl = parts[1].ifBlank { null },
                title = parts[2],
                sourceLabel = parts[3],
                sourceKind = LibrarySourceKind.valueOf(parts[4]),
                mediaType = parts[5].takeIf { it.isNotBlank() }?.let(MediaType::valueOf),
                userAgent = parts[6].ifBlank { null },
                thumbnailUrl = parts[7].ifBlank { null },
                lastPlayedAt = parts[8].toLong(),
                positionMs = parts[9].toLong().coerceAtLeast(0L),
                durationMs = parts[10].toLong().coerceAtLeast(0L),
                pageTitle = parts.getOrNull(11)?.ifBlank { null },
                videoName = parts.getOrNull(12)?.ifBlank { null },
                folderId = parts.getOrNull(13)?.ifBlank { null }
            )
        }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

object LibraryStore {
    fun save(context: Context, entries: List<LibraryEntry>) {
        // Playback progress updates frequently. Publish the new index immediately while Android
        // writes it asynchronously, so a Library refresh never waits for a disk fsync on the UI thread.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, LibraryCodec.encode(entries))
            .apply()
    }

    fun load(context: Context): List<LibraryEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return LibraryCodec.decode(raw)
    }
}
