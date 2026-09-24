package com.miowser.app.downloader

import android.content.Context

private const val PREFS_NAME = "downloads"
private const val KEY_DOWNLOADS = "downloads"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'
private const val FIELD_COUNT = 16
private const val PRE_REQUEST_METADATA_FIELD_COUNT = 14
private const val PRE_PREFERRED_NAME_FIELD_COUNT = 13
private const val PRE_ELAPSED_FIELD_COUNT = 12
private const val PRE_EXTENSION_FIELD_COUNT = 11
private const val PRE_SAVED_URI_FIELD_COUNT = 10

/**
 * A download's persisted presentation record. The non-secret User-Agent and Referer survive a
 * restart so a recovery worker can reconstruct its request. Raw cookies deliberately do not:
 * recovery reacquires the current session from CookieManager instead of retaining credentials in
 * SharedPreferences indefinitely. Byte ranges and completed segments live in the separately
 * versioned [DownloadRecoveryStore], keeping history migrations independent from transfer state.
 *
 * [savedUri] is a plain String, not a Uri, to keep this file's whole point — pure logic, no
 * Android dependency, directly unit-testable — intact; Download.savedUri (the real Uri) is
 * converted at the DownloadQueue boundary instead.
 */
data class SavedDownload(
    val id: Long,
    val sourceUrl: String,
    val pageUrl: String,
    val kind: DownloadKind,
    val displayName: String,
    val outputBaseName: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    val savedUri: String? = null,
    val suggestedExtension: String? = null,
    val elapsedActiveMillis: Long = 0L,
    val preferredOutputBaseName: String? = null,
    val userAgent: String = "",
    val referer: String = ""
)

/**
 * Encodes/decodes the download list to a flat string — pure logic, no Android dependency, so
 * it's directly unit-testable. Same delimited-text shape as TabSessionCodec and for the same
 * reasons (see its own doc comment): the record shape is simple enough that a JSON dependency
 * would be overhead for no real benefit.
 */
object DownloadCodec {
    fun encode(downloads: List<SavedDownload>): String =
        downloads.joinToString(RECORD_DELIMITER.toString()) { d ->
            listOf(
                d.id.toString(),
                sanitize(d.sourceUrl),
                sanitize(d.pageUrl),
                d.kind.name,
                sanitize(d.displayName),
                sanitize(d.outputBaseName),
                d.status.name,
                d.bytesDownloaded.toString(),
                d.totalBytes.toString(),
                d.errorMessage?.let(::sanitize) ?: "",
                d.savedUri?.let(::sanitize) ?: "",
                d.suggestedExtension?.let(::sanitize) ?: "",
                d.elapsedActiveMillis.coerceAtLeast(0L).toString(),
                d.preferredOutputBaseName?.let(::sanitize) ?: "",
                sanitize(d.userAgent),
                sanitize(d.referer)
            ).joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<SavedDownload> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_DELIMITER).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): SavedDownload? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != FIELD_COUNT &&
            parts.size != PRE_REQUEST_METADATA_FIELD_COUNT &&
            parts.size != PRE_PREFERRED_NAME_FIELD_COUNT &&
            parts.size != PRE_ELAPSED_FIELD_COUNT &&
            parts.size != PRE_EXTENSION_FIELD_COUNT &&
            parts.size != PRE_SAVED_URI_FIELD_COUNT
        ) return null
        return runCatching {
            SavedDownload(
                id = parts[0].toLong(),
                sourceUrl = parts[1],
                pageUrl = parts[2],
                kind = DownloadKind.valueOf(parts[3]),
                displayName = parts[4],
                outputBaseName = parts[5],
                status = DownloadStatus.valueOf(parts[6]),
                bytesDownloaded = parts[7].toLong(),
                totalBytes = parts[8].toLong(),
                errorMessage = parts[9].ifBlank { null },
                // Absent entirely on a LEGACY_FIELD_COUNT line (no index 10 at all — getOrNull
                // handles that), blank on a FIELD_COUNT line whose download never completed.
                // Either way: null.
                savedUri = parts.getOrNull(10)?.ifBlank { null },
                suggestedExtension = parts.getOrNull(11)?.ifBlank { null },
                elapsedActiveMillis = parts.getOrNull(12)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                preferredOutputBaseName = parts.getOrNull(13)?.ifBlank { null },
                userAgent = parts.getOrNull(14).orEmpty(),
                referer = parts.getOrNull(15).orEmpty()
            )
        }.getOrNull()
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/** Thin SharedPreferences wrapper around [DownloadCodec] — the only Context-dependent part. */
object DownloadStore {
    // commit(), not apply(): confirmed on-device as a real data-loss bug, not just a theoretical
    // one — apply() queues the disk write on SharedPreferences' own worker thread and returns
    // immediately, so it's only "eventually" durable. DownloadQueue.persist() (the caller here)
    // is itself called from a background executor thread right after a download finishes, and
    // under real memory pressure (observed directly in logcat as sustained background GC — a
    // long session with several tabs/downloads alive is enough to trigger it) that queued write
    // can still be sitting unflushed when the process dies (a real force-stop reproduced this: a
    // download that showed "Done" in the UI was gone after relaunch, and the persisted file was
    // empty). commit() blocks until the write actually lands, which is exactly the guarantee
    // needed here — the cost is a synchronous block, harmless since this is only ever called a
    // few times per download, never per-frame or per-keystroke.
    fun save(context: Context, downloads: List<SavedDownload>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DOWNLOADS, DownloadCodec.encode(downloads))
            .commit()
    }

    fun load(context: Context): List<SavedDownload> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DOWNLOADS, null)
            ?: return emptyList()
        return DownloadCodec.decode(raw)
    }
}
