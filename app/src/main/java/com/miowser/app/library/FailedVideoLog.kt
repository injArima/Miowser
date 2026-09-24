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
    private val lock = Any()

    fun load(context: Context, now: Long = System.currentTimeMillis()): List<FailedVideoRecord> =
        synchronized(lock) { loadLocked(context, now) }

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
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        val decoded = FailedVideoCodec.decode(raw)
        val kept = FailedVideoCodec.retained(decoded, now)
        if (kept.size != decoded.size) persist(context, kept)
        return kept.sortedByDescending(FailedVideoRecord::lastActionAt)
    }

    private fun persist(context: Context, records: List<FailedVideoRecord>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, FailedVideoCodec.encode(records)).apply()
    }
}
