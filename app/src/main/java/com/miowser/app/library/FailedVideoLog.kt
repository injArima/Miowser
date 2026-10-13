package com.miowser.app.library

import android.content.Context
import java.util.Base64
import java.util.UUID

private const val RETENTION_MS = 90L * 24L * 60L * 60L * 1_000L

data class FailedVideoRecord(
    val id: String,
    val videoName: String,
    val pageTitle: String,
    val domain: String,
    val pageUrl: String,
    val lastActionAt: Long
)

/** Kept in a separate preference so opening the Library never decodes this optional log. */
object FailedVideoCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private fun encoded(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decoded(value: String): String = String(decoder.decode(value), Charsets.UTF_8)

    fun encode(records: List<FailedVideoRecord>): String = records.joinToString("\n") { record ->
        listOf(
            record.id,
            encoded(record.videoName),
            encoded(record.pageTitle),
            encoded(record.domain),
            encoded(record.pageUrl),
            record.lastActionAt.toString()
        ).joinToString("\t")
    }

    fun decode(raw: String): List<FailedVideoRecord> = raw.lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != 6 || fields[0].isBlank()) return@mapNotNull null
        runCatching {
            FailedVideoRecord(
                fields[0], decoded(fields[1]), decoded(fields[2]), decoded(fields[3]),
                decoded(fields[4]), fields[5].toLong()
            )
        }.getOrNull()
    }.toList()

    fun retained(records: List<FailedVideoRecord>, now: Long): List<FailedVideoRecord> =
        records.filter { it.lastActionAt >= now - RETENTION_MS && it.lastActionAt <= now }
}

object FailedVideoLog {
    private const val PREFS = "failed_video_log"
    private const val KEY = "records"
    private const val SAVED_KEY = "saved_records"
    private val lock = Any()
    private var cached: List<FailedVideoRecord>? = null
    private var savedCached: List<FailedVideoRecord>? = null

    fun load(context: Context, now: Long = System.currentTimeMillis()): List<FailedVideoRecord> =
        synchronized(lock) { loadLocked(context, now) }

    /** Saved references use the same small text record, but have no expiry. */
    fun loadSaved(context: Context): List<FailedVideoRecord> = synchronized(lock) {
        savedLocked(context).sortedByDescending(FailedVideoRecord::lastActionAt)
    }

    fun saveFromLibrary(context: Context, entry: LibraryEntry, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val record = savedReferenceFromLibrary(entry, now)
            val saved = savedLocked(context)
            if (saved.any { it.videoName == record.videoName && it.pageUrl == record.pageUrl }) return@synchronized false
            persistSaved(context, listOf(record) + saved)
            true
        }

    /** Moves one expiring record into the permanent section in a single preference commit. */
    fun moveToSaved(context: Context, id: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val current = loadLocked(context, now)
            val saved = savedLocked(context)
            val (nextCurrent, nextSaved) = moveVideoRecord(current, saved, id, now)
                ?: return@synchronized false
            persistAll(context, nextCurrent, nextSaved)
            true
        }

    fun removeSaved(context: Context, id: String): Boolean = synchronized(lock) {
        val current = savedLocked(context)
        val kept = current.filterNot { it.id == id }
        if (kept.size == current.size) return@synchronized false
        persistSaved(context, kept)
        true
    }

    fun record(
        context: Context,
        videoName: String,
        pageTitle: String,
        pageUrl: String,
        now: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val current = loadLocked(context, now)
        val record = FailedVideoRecord(
            id = UUID.randomUUID().toString(),
            videoName = videoName.ifBlank { "Untitled video" },
            pageTitle = pageTitle.ifBlank { "Unknown page" },
            domain = libraryDomain(pageUrl) ?: "Unknown website",
            pageUrl = pageUrl,
            lastActionAt = now
        )
        persist(context, listOf(record) + current)
    }

    fun touch(context: Context, id: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val current = loadLocked(context, now)
        val updated = current.map { if (it.id == id) it.copy(lastActionAt = now) else it }
        if (updated != current) persist(context, updated)
    }

    private fun loadLocked(context: Context, now: Long): List<FailedVideoRecord> {
        val decoded = recentLockedRaw(context)
        val kept = FailedVideoCodec.retained(decoded, now)
        if (kept.size != decoded.size) persist(context, kept)
        return kept.sortedByDescending(FailedVideoRecord::lastActionAt)
    }

    private fun recentLockedRaw(context: Context): List<FailedVideoRecord> = cached ?: run {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        FailedVideoCodec.decode(raw).also { cached = it }
    }

    private fun savedLocked(context: Context): List<FailedVideoRecord> = savedCached ?: run {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SAVED_KEY, "").orEmpty()
        FailedVideoCodec.decode(raw).also { savedCached = it }
    }

    private fun persistSaved(context: Context, records: List<FailedVideoRecord>) {
        persistAll(context, recentLockedRaw(context), records)
    }

    private fun persist(context: Context, records: List<FailedVideoRecord>) {
        persistAll(context, records, savedLocked(context))
    }

    /** Every ordered write is a complete snapshot, including after a failed write is retried. */
    private fun persistAll(context: Context, recent: List<FailedVideoRecord>, saved: List<FailedVideoRecord>) {
        val recentSnapshot = recent.toList()
        val savedSnapshot = saved.toList()
        cached = recentSnapshot
        savedCached = savedSnapshot
        val appContext = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("failed-video-log") {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, FailedVideoCodec.encode(recentSnapshot))
                .putString(SAVED_KEY, FailedVideoCodec.encode(savedSnapshot)).commit()
        }
    }
}

internal fun moveVideoRecord(
    recent: List<FailedVideoRecord>,
    saved: List<FailedVideoRecord>,
    id: String,
    now: Long
): Pair<List<FailedVideoRecord>, List<FailedVideoRecord>>? {
    val record = recent.firstOrNull { it.id == id } ?: return null
    val nextSaved = if (saved.any { it.videoName == record.videoName && it.pageUrl == record.pageUrl }) saved
        else listOf(record.copy(lastActionAt = now)) + saved
    return recent.filterNot { it.id == id } to nextSaved
}

internal fun savedReferenceFromLibrary(entry: LibraryEntry, now: Long): FailedVideoRecord = FailedVideoRecord(
    id = UUID.randomUUID().toString(),
    videoName = entry.videoName?.takeIf(String::isNotBlank) ?: entry.title.ifBlank { "Untitled video" },
    pageTitle = entry.pageTitle?.takeIf(String::isNotBlank) ?: entry.title.ifBlank { "Unknown page" },
    domain = libraryDomain(entry.pageUrl) ?: if (entry.sourceKind == LibrarySourceKind.LOCAL_FILE) "Local file" else "Unknown website",
    pageUrl = entry.pageUrl.orEmpty(),
    lastActionAt = now
)
