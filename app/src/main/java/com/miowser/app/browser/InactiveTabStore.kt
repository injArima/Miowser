package com.miowser.app.browser

import android.content.Context
import com.miowser.app.persistence.Persistence
import com.miowser.app.settings.InactiveTabRetention
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
    private val mutableRecords = mutableStateListOf<InactiveTabRecord>()
    val records: List<InactiveTabRecord> get() = mutableRecords
    private var loaded = false

    suspend fun restoreAsync(context: Context, retention: InactiveTabRetention) {
        Persistence.requireOwner()
        if (!loaded) {
            val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                InactiveTabCodec.decode(context.getSharedPreferences(INACTIVE_PREFS, Context.MODE_PRIVATE)
                    .getString(INACTIVE_RECORDS, "").orEmpty())
            }
            if (!loaded) { mutableRecords.addAll(records); loaded = true }
        }
        prune(context, retention, System.currentTimeMillis())
    }

    fun restore(context: Context, retention: InactiveTabRetention, now: Long = System.currentTimeMillis()) {
        Persistence.requireOwner()
        if (!loaded) {
            val raw = context.getSharedPreferences(INACTIVE_PREFS, Context.MODE_PRIVATE)
                .getString(INACTIVE_RECORDS, "").orEmpty()
            mutableRecords.clear()
            mutableRecords.addAll(InactiveTabCodec.decode(raw))
            loaded = true
        }
        prune(context, retention, now)
    }

    fun archive(context: Context, newRecords: List<InactiveTabRecord>) {
        Persistence.requireOwner()
        if (newRecords.isEmpty()) return
        mutableRecords.addAll(0, newRecords)
        persist(context)
    }

    fun take(context: Context, id: String): InactiveTabRecord? {
        Persistence.requireOwner()
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0) return null
        return mutableRecords.removeAt(index).also { persist(context) }
    }

    fun delete(context: Context, id: String) {
        Persistence.requireOwner()
        if (mutableRecords.removeAll { it.id == id }) persist(context)
    }

    fun prune(context: Context, retention: InactiveTabRetention, now: Long = System.currentTimeMillis()) {
        Persistence.requireOwner()
        if (retention == InactiveTabRetention.NEVER) return
        if (mutableRecords.removeAll { InactiveTabPolicy.isExpired(it.archivedAt, now, retention) }) {
            persist(context)
        }
    }

    private fun persist(context: Context) {
        Persistence.requireOwner()
        val snapshot = mutableRecords.toList()
        val appContext = context.applicationContext
        Persistence.submitSnapshot("inactive-tabs") {
            appContext.getSharedPreferences(INACTIVE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(INACTIVE_RECORDS, InactiveTabCodec.encode(snapshot)).commit()
        }
    }
}
