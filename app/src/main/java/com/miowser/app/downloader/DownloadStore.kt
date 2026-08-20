package com.miowser.app.downloader

import android.content.Context

private const val PREFS_NAME = "downloads"
private const val KEY_DOWNLOADS = "downloads"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'
private const val FIELD_COUNT = 10

/**
 * A download's persisted record — deliberately narrower than [Download] itself: userAgent,
 * cookies, and the request-time Referer don't survive a restart. Those were only ever needed to
 * authenticate the request while it was actually running; keeping session cookies around
 * indefinitely in SharedPreferences would be a real, ongoing privacy cost for no benefit once a
 * download is done, failed, or gone — there's no resume support yet that would make reusing them
 * meaningful anyway (see project notes on that gap).
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
    val errorMessage: String?
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
                d.errorMessage?.let(::sanitize) ?: ""
            ).joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<SavedDownload> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_DELIMITER).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): SavedDownload? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != FIELD_COUNT) return null
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
                errorMessage = parts[9].ifBlank { null }
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
