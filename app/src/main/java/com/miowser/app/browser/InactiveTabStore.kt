package com.miowser.app.browser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.util.UUID

private const val INACTIVE_PREFS = "inactive_tabs"
private const val INACTIVE_RECORDS = "records"
private const val INACTIVE_FIELD = '\t'
private const val INACTIVE_LINE = '\n'

data class InactiveTabRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val lastActiveAt: Long,
    val archivedAt: Long,
    val reason: InactiveTabReason
)

object InactiveTabCodec {
    fun encode(records: List<InactiveTabRecord>): String = records.joinToString(INACTIVE_LINE.toString()) { record ->
        listOf(
            clean(record.id), clean(record.title), clean(record.url), record.lastActiveAt.toString(),
            record.archivedAt.toString(), record.reason.name
        ).joinToString(INACTIVE_FIELD.toString())
    }

    fun decode(raw: String): List<InactiveTabRecord> = raw.split(INACTIVE_LINE).mapNotNull { line ->
        val fields = line.split(INACTIVE_FIELD, limit = 6)
        if (fields.size != 6 || fields[2].isBlank()) return@mapNotNull null
        val reason = runCatching { InactiveTabReason.valueOf(fields[5]) }.getOrNull() ?: return@mapNotNull null
        InactiveTabRecord(
            id = fields[0].ifBlank { UUID.randomUUID().toString() },
            title = fields[1],
            url = fields[2],
            lastActiveAt = fields[3].toLongOrNull() ?: 0L,
            archivedAt = fields[4].toLongOrNull() ?: 0L,
            reason = reason
        )
    }

    private fun clean(value: String) = value.replace(INACTIVE_FIELD, ' ').replace(INACTIVE_LINE, ' ')
}

object InactiveTabs {
    val records = mutableStateListOf<InactiveTabRecord>()
    private var loaded = false

    fun restore(context: Context, autoDeleteEnabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (!loaded) {
            loaded = true
            val raw = context.getSharedPreferences(INACTIVE_PREFS, Context.MODE_PRIVATE)
                .getString(INACTIVE_RECORDS, "").orEmpty()
            records.clear()
            records.addAll(InactiveTabCodec.decode(raw))
        }
        prune(context, autoDeleteEnabled, now)
    }

    fun archive(context: Context, newRecords: List<InactiveTabRecord>) {
        if (newRecords.isEmpty()) return
        records.addAll(0, newRecords)
        persist(context)
    }

    fun take(context: Context, id: String): InactiveTabRecord? {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return null
        return records.removeAt(index).also { persist(context) }
    }

    fun delete(context: Context, id: String) {
        if (records.removeAll { it.id == id }) persist(context)
    }

    fun prune(context: Context, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        if (!enabled) return
        if (records.removeAll { InactiveTabPolicy.isExpired(it.archivedAt, now) }) {
            persist(context)
        }
    }

    private fun persist(context: Context) {
        context.applicationContext.getSharedPreferences(INACTIVE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(INACTIVE_RECORDS, InactiveTabCodec.encode(records))
            .commit()
    }
}
