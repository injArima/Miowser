package com.miowser.app.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

private const val SPEED_DIAL_PREFS = "browser_speed_dials"
private const val SPEED_DIAL_KEY = "entries"
private const val SPEED_DIAL_FOLDER_KEY = "folders"
private const val SPEED_DIAL_SORT_KEY = "sort"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'

data class SpeedDial(
    val id: Long,
    val title: String,
    val url: String,
    val folderId: Long? = null
)

data class SpeedDialFolder(val id: Long, val name: String)

enum class SpeedDialSort(val label: String) {
    ADDED("Added order"),
    NAME_ASCENDING("Name A–Z"),
    NAME_DESCENDING("Name Z–A")
}

object SpeedDialCodec {
    fun encode(entries: List<SpeedDial>): String = entries.joinToString(RECORD_DELIMITER.toString()) { entry ->
        listOf(
            entry.id.toString(),
            sanitize(entry.title),
            sanitize(entry.url),
            entry.folderId?.toString().orEmpty()
        )
            .joinToString(FIELD_DELIMITER.toString())
    }

    fun decode(raw: String): List<SpeedDial> = raw.split(RECORD_DELIMITER).mapNotNull { line ->
        val parts = line.split(FIELD_DELIMITER, limit = 4)
        if (parts.size !in 3..4 || parts[1].isBlank() || parts[2].isBlank()) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val folderId = parts.getOrNull(3)?.takeIf(String::isNotBlank)?.toLongOrNull()
        SpeedDial(id = id, title = parts[1], url = parts[2], folderId = folderId)
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ').trim()
}

object SpeedDialFolderCodec {
    fun encode(folders: List<SpeedDialFolder>): String = folders.joinToString(RECORD_DELIMITER.toString()) { folder ->
        "${folder.id}$FIELD_DELIMITER${sanitize(folder.name)}"
    }

    fun decode(raw: String): List<SpeedDialFolder> = raw.split(RECORD_DELIMITER).mapNotNull { line ->
        val parts = line.split(FIELD_DELIMITER, limit = 2)
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val name = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        SpeedDialFolder(id, name)
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ').trim()
}

object SpeedDials {
    val entries = mutableStateListOf<SpeedDial>()
    val folders = mutableStateListOf<SpeedDialFolder>()
    private val idCounter = AtomicLong(0L)
    private val folderIdCounter = AtomicLong(0L)
    var sort by mutableStateOf(SpeedDialSort.ADDED)
        private set

    fun restore(context: Context) {
        val raw = context.getSharedPreferences(SPEED_DIAL_PREFS, Context.MODE_PRIVATE)
            .getString(SPEED_DIAL_KEY, null)
        val prefs = context.getSharedPreferences(SPEED_DIAL_PREFS, Context.MODE_PRIVATE)
        val restored = raw?.let(SpeedDialCodec::decode).orEmpty()
        val restoredFolders = prefs.getString(SPEED_DIAL_FOLDER_KEY, null)
            ?.let(SpeedDialFolderCodec::decode)
            .orEmpty()
        sort = prefs.getString(SPEED_DIAL_SORT_KEY, null)
            ?.let { runCatching { SpeedDialSort.valueOf(it) }.getOrNull() }
            ?: SpeedDialSort.ADDED
        entries.clear()
        entries.addAll(restored)
        folders.clear()
        folders.addAll(restoredFolders)
        restored.maxOfOrNull(SpeedDial::id)?.let(::ensureNextIdAbove)
        restoredFolders.maxOfOrNull(SpeedDialFolder::id)?.let(::ensureNextFolderIdAbove)
    }

    /** Adds or edits one entry. URLs stay unique; saving an existing URL updates that card. */
    fun save(
        context: Context,
        id: Long?,
        title: String,
        address: String,
        folderId: Long? = null
    ): SpeedDial? {
        val url = normalizeSpeedDialAddress(address)
        if (url.isBlank()) return null
        val finalTitle = title.trim().ifBlank { defaultSpeedDialTitle(url) }
        val duplicateIndex = entries.indexOfFirst { it.url.equals(url, ignoreCase = true) && it.id != id }
        val targetIndex = id?.let { target -> entries.indexOfFirst { it.id == target } } ?: -1
        val targetId = when {
            targetIndex >= 0 -> id!!
            duplicateIndex >= 0 -> entries[duplicateIndex].id
            else -> idCounter.getAndIncrement()
        }
        val validFolderId = folderId?.takeIf { folder -> folders.any { it.id == folder } }
        val updated = SpeedDial(targetId, finalTitle, url, validFolderId)
        when {
            targetIndex >= 0 -> {
                entries[targetIndex] = updated
                if (duplicateIndex >= 0) entries.removeAt(
                    if (duplicateIndex > targetIndex) duplicateIndex else duplicateIndex
                )
            }
            duplicateIndex >= 0 -> entries[duplicateIndex] = updated
            else -> entries.add(updated)
        }
        persist(context)
        return updated
    }

    fun move(context: Context, id: Long, folderId: Long?) {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val validFolderId = folderId?.takeIf { folder -> folders.any { it.id == folder } }
        entries[index] = entries[index].copy(folderId = validFolderId)
        persist(context)
    }

    fun moveMany(context: Context, ids: Set<Long>, folderId: Long?) {
        if (ids.isEmpty()) return
        val validFolderId = folderId?.takeIf { folder -> folders.any { it.id == folder } }
        entries.indices.forEach { index ->
            if (entries[index].id in ids) entries[index] = entries[index].copy(folderId = validFolderId)
        }
        persist(context)
    }

    fun setSort(context: Context, value: SpeedDialSort) {
        sort = value
        persist(context)
    }

    fun addFolder(context: Context, name: String): SpeedDialFolder? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val existing = folders.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) return existing
        val folder = SpeedDialFolder(folderIdCounter.getAndIncrement(), cleanName)
        folders.add(folder)
        persist(context)
        return folder
    }

    fun renameFolder(context: Context, id: Long, name: String) {
        val cleanName = name.trim()
        val index = folders.indexOfFirst { it.id == id }
        if (index < 0 || cleanName.isBlank()) return
        folders[index] = folders[index].copy(name = cleanName)
        persist(context)
    }

    /** Removes only the folder. Its speed dials return safely to the homepage root. */
    fun removeFolder(context: Context, id: Long): SpeedDialFolder? {
        val index = folders.indexOfFirst { it.id == id }
        if (index < 0) return null
        entries.indices.forEach { entryIndex ->
            if (entries[entryIndex].folderId == id) {
                entries[entryIndex] = entries[entryIndex].copy(folderId = null)
            }
        }
        return folders.removeAt(index).also { persist(context) }
    }

    fun remove(context: Context, id: Long): SpeedDial? {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return null
        return entries.removeAt(index).also { persist(context) }
    }

    fun removeMany(context: Context, ids: Set<Long>): List<SpeedDial> {
        if (ids.isEmpty()) return emptyList()
        val removed = entries.filter { it.id in ids }
        entries.removeAll { it.id in ids }
        persist(context)
        return removed
    }

    fun restoreRemoved(context: Context, entry: SpeedDial) {
        if (entries.none { it.id == entry.id }) entries.add(entry)
        ensureNextIdAbove(entry.id)
        persist(context)
    }

    fun restoreRemoved(context: Context, removed: List<SpeedDial>) {
        removed.forEach { entry ->
            if (entries.none { it.id == entry.id }) entries.add(entry)
            ensureNextIdAbove(entry.id)
        }
        persist(context)
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(SPEED_DIAL_PREFS, Context.MODE_PRIVATE).edit()
            .putString(SPEED_DIAL_KEY, SpeedDialCodec.encode(entries))
            .putString(SPEED_DIAL_FOLDER_KEY, SpeedDialFolderCodec.encode(folders))
            .putString(SPEED_DIAL_SORT_KEY, sort.name)
            .commit()
    }

    private fun ensureNextIdAbove(id: Long) {
        idCounter.updateAndGet { current -> maxOf(current, id + 1L) }
    }

    private fun ensureNextFolderIdAbove(id: Long) {
        folderIdCounter.updateAndGet { current -> maxOf(current, id + 1L) }
    }
}

internal fun sortSpeedDials(entries: List<SpeedDial>, sort: SpeedDialSort): List<SpeedDial> = when (sort) {
    SpeedDialSort.ADDED -> entries
    SpeedDialSort.NAME_ASCENDING -> entries.sortedBy { defaultSpeedDialTitle(it.url).lowercase() }
    SpeedDialSort.NAME_DESCENDING -> entries.sortedByDescending { defaultSpeedDialTitle(it.url).lowercase() }
}

internal fun normalizeSpeedDialAddress(address: String): String {
    val trimmed = address.trim()
    val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        .find(trimmed)
        ?.value
        ?.dropLast(1)
        ?.lowercase()
    if (explicitScheme != null && explicitScheme != "http" && explicitScheme != "https") return ""
    val resolved = UrlOrSearchResolver.resolve(trimmed)
    val scheme = runCatching { URI(resolved).scheme?.lowercase() }.getOrNull()
    return resolved.takeIf { scheme == "http" || scheme == "https" }.orEmpty()
}

internal fun defaultSpeedDialTitle(url: String): String = runCatching { URI(url).host }
    .getOrNull()
    ?.removePrefix("www.")
    ?.takeIf(String::isNotBlank)
    ?: url.take(40)
