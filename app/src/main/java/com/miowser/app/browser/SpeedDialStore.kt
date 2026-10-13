package com.miowser.app.browser

import android.content.Context
import com.miowser.app.persistence.Persistence
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
    val folderId: Long? = null,
    val autoTitle: Boolean = false
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
            entry.folderId?.toString().orEmpty(),
            entry.autoTitle.toString()
        )
            .joinToString(FIELD_DELIMITER.toString())
    }

    fun decode(raw: String): List<SpeedDial> = raw.split(RECORD_DELIMITER).mapNotNull { line ->
        val parts = line.split(FIELD_DELIMITER, limit = 5)
        if (parts.size !in 3..5 || parts[1].isBlank() || parts[2].isBlank()) return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val folderId = parts.getOrNull(3)?.takeIf(String::isNotBlank)?.toLongOrNull()
        val autoTitle = parts.getOrNull(4)?.toBooleanStrictOrNull()
            ?: (parts[1] == defaultSpeedDialTitle(parts[2]))
        SpeedDial(id = id, title = parts[1], url = parts[2], folderId = folderId, autoTitle = autoTitle)
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
    private val mutableEntries = mutableStateListOf<SpeedDial>()
    val entries: List<SpeedDial> get() = mutableEntries
    private val mutableFolders = mutableStateListOf<SpeedDialFolder>()
    val folders: List<SpeedDialFolder> get() = mutableFolders
    private val idCounter = AtomicLong(0L)
    private val folderIdCounter = AtomicLong(0L)
    var sort by mutableStateOf(SpeedDialSort.ADDED)
        private set

    private var loaded = false

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (loaded) return
        val (entries, folders, savedSort) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = context.getSharedPreferences(SPEED_DIAL_PREFS, Context.MODE_PRIVATE)
            Triple(SpeedDialCodec.decode(prefs.getString(SPEED_DIAL_KEY, "").orEmpty()),
                SpeedDialFolderCodec.decode(prefs.getString(SPEED_DIAL_FOLDER_KEY, "").orEmpty()),
                prefs.getString(SPEED_DIAL_SORT_KEY, null)?.let { runCatching { SpeedDialSort.valueOf(it) }.getOrNull() }
                    ?: SpeedDialSort.ADDED)
        }
        if (loaded) return
        mutableEntries.addAll(entries)
        mutableFolders.addAll(folders)
        sort = savedSort
        entries.maxOfOrNull(SpeedDial::id)?.let(::ensureNextIdAbove)
        folders.maxOfOrNull(SpeedDialFolder::id)?.let(::ensureNextFolderIdAbove)
        loaded = true
    }

    fun restore(context: Context) {
        Persistence.requireOwner()
        if (loaded) return
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
        mutableEntries.clear()
        mutableEntries.addAll(restored)
        mutableFolders.clear()
        mutableFolders.addAll(restoredFolders)
        restored.maxOfOrNull(SpeedDial::id)?.let(::ensureNextIdAbove)
        restoredFolders.maxOfOrNull(SpeedDialFolder::id)?.let(::ensureNextFolderIdAbove)
        loaded = true
    }

    /** Adds or edits one entry. URLs stay unique; saving an existing URL updates that card. */
    fun save(
        context: Context,
        id: Long?,
        title: String,
        address: String,
        folderId: Long? = null
    ): SpeedDial? {
        Persistence.requireOwner()
        val url = normalizeSpeedDialAddress(address)
        if (url.isBlank()) return null
        val duplicateIndex = mutableEntries.indexOfFirst { it.url.equals(url, ignoreCase = true) && it.id != id }
        val targetIndex = id?.let { target -> mutableEntries.indexOfFirst { it.id == target } } ?: -1
        val duplicate = mutableEntries.getOrNull(duplicateIndex)
        val preserveExistingName = id == null && title.isBlank() && duplicate != null
        val finalTitle = when {
            preserveExistingName -> duplicate!!.title
            title.isBlank() -> defaultSpeedDialTitle(url)
            else -> title.trim()
        }
        val autoTitle = if (preserveExistingName) duplicate!!.autoTitle else title.isBlank()
        val targetId = when {
            targetIndex >= 0 -> id!!
            duplicateIndex >= 0 -> mutableEntries[duplicateIndex].id
            else -> idCounter.getAndIncrement()
        }
        val validFolderId = folderId?.takeIf { folder -> mutableFolders.any { it.id == folder } }
        val updated = SpeedDial(targetId, finalTitle, url, validFolderId, autoTitle = autoTitle)
        when {
            targetIndex >= 0 -> {
                mutableEntries[targetIndex] = updated
                if (duplicateIndex >= 0) mutableEntries.removeAt(
                    if (duplicateIndex > targetIndex) duplicateIndex else duplicateIndex
                )
            }
            duplicateIndex >= 0 -> mutableEntries[duplicateIndex] = updated
            else -> mutableEntries.add(updated)
        }
        persist(context)
        return updated
    }

    fun move(context: Context, id: Long, folderId: Long?) {
        Persistence.requireOwner()
        val index = mutableEntries.indexOfFirst { it.id == id }
        if (index < 0) return
        val validFolderId = folderId?.takeIf { folder -> mutableFolders.any { it.id == folder } }
        mutableEntries[index] = mutableEntries[index].copy(folderId = validFolderId)
        persist(context)
    }

    /** Site titles update only entries whose names the user has not edited. */
    fun updateAutoTitle(context: Context, url: String, title: String) {
        Persistence.requireOwner()
        val cleanTitle = title.trim().replace(Regex("\\s+"), " ").take(160)
        if (cleanTitle.isBlank() || cleanTitle.equals(url, ignoreCase = true) ||
            cleanTitle.equals(defaultSpeedDialTitle(url), ignoreCase = true) ||
            cleanTitle.equals("New Tab", ignoreCase = true) ||
            cleanTitle.equals("Just a moment...", ignoreCase = true) ||
            cleanTitle.equals("Loading...", ignoreCase = true) ||
            cleanTitle.equals("about:blank", ignoreCase = true)
        ) return
        var changed = false
        mutableEntries.indices.forEach { index ->
            val entry = mutableEntries[index]
            if (entry.autoTitle && entry.title == defaultSpeedDialTitle(entry.url) &&
                entry.url.trimEnd('/') == url.trimEnd('/') && entry.title != cleanTitle
            ) {
                mutableEntries[index] = entry.copy(title = cleanTitle)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    fun moveMany(context: Context, ids: Set<Long>, folderId: Long?) {
        Persistence.requireOwner()
        if (ids.isEmpty()) return
        val validFolderId = folderId?.takeIf { folder -> mutableFolders.any { it.id == folder } }
        mutableEntries.indices.forEach { index ->
            if (mutableEntries[index].id in ids) mutableEntries[index] = mutableEntries[index].copy(folderId = validFolderId)
        }
        persist(context)
    }

    fun setSort(context: Context, value: SpeedDialSort) {
        Persistence.requireOwner()
        sort = value
        persist(context)
    }

    fun addFolder(context: Context, name: String): SpeedDialFolder? {
        Persistence.requireOwner()
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val existing = mutableFolders.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        if (existing != null) return existing
        val folder = SpeedDialFolder(folderIdCounter.getAndIncrement(), cleanName)
        mutableFolders.add(folder)
        persist(context)
        return folder
    }

    fun renameFolder(context: Context, id: Long, name: String) {
        Persistence.requireOwner()
        val cleanName = name.trim()
        val index = mutableFolders.indexOfFirst { it.id == id }
        if (index < 0 || cleanName.isBlank()) return
        mutableFolders[index] = mutableFolders[index].copy(name = cleanName)
        persist(context)
    }

    /** Removes only the folder. Its speed dials return safely to the homepage root. */
    fun removeFolder(context: Context, id: Long): SpeedDialFolder? {
        Persistence.requireOwner()
        val index = mutableFolders.indexOfFirst { it.id == id }
        if (index < 0) return null
        mutableEntries.indices.forEach { entryIndex ->
            if (mutableEntries[entryIndex].folderId == id) {
                mutableEntries[entryIndex] = mutableEntries[entryIndex].copy(folderId = null)
            }
        }
        return mutableFolders.removeAt(index).also { persist(context) }
    }

    fun remove(context: Context, id: Long): SpeedDial? {
        Persistence.requireOwner()
        val index = mutableEntries.indexOfFirst { it.id == id }
        if (index < 0) return null
        return mutableEntries.removeAt(index).also { persist(context) }
    }

    fun removeMany(context: Context, ids: Set<Long>): List<SpeedDial> {
        Persistence.requireOwner()
        if (ids.isEmpty()) return emptyList()
        val removed = mutableEntries.filter { it.id in ids }
        mutableEntries.removeAll { it.id in ids }
        persist(context)
        return removed
    }

    fun restoreRemoved(context: Context, entry: SpeedDial) {
        Persistence.requireOwner()
        if (mutableEntries.none { it.id == entry.id }) mutableEntries.add(entry)
        ensureNextIdAbove(entry.id)
        persist(context)
    }

    fun restoreRemoved(context: Context, removed: List<SpeedDial>) {
        Persistence.requireOwner()
        removed.forEach { entry ->
            if (mutableEntries.none { it.id == entry.id }) mutableEntries.add(entry)
            ensureNextIdAbove(entry.id)
        }
        persist(context)
    }

    private fun persist(context: Context) {
        Persistence.requireOwner()
        val savedEntries = mutableEntries.toList()
        val savedFolders = mutableFolders.toList()
        val savedSort = sort
        val appContext = context.applicationContext
        Persistence.submitSnapshot("speed-dials") {
            appContext.getSharedPreferences(SPEED_DIAL_PREFS, Context.MODE_PRIVATE).edit()
                .putString(SPEED_DIAL_KEY, SpeedDialCodec.encode(savedEntries))
                .putString(SPEED_DIAL_FOLDER_KEY, SpeedDialFolderCodec.encode(savedFolders))
                .putString(SPEED_DIAL_SORT_KEY, savedSort.name).commit()
        }
    }

    private fun ensureNextIdAbove(id: Long) {
        Persistence.requireOwner()
        idCounter.updateAndGet { current -> maxOf(current, id + 1L) }
    }

    private fun ensureNextFolderIdAbove(id: Long) {
        Persistence.requireOwner()
        folderIdCounter.updateAndGet { current -> maxOf(current, id + 1L) }
    }
}

internal fun sortSpeedDials(entries: List<SpeedDial>, sort: SpeedDialSort): List<SpeedDial> = when (sort) {
    SpeedDialSort.ADDED -> entries
    SpeedDialSort.NAME_ASCENDING -> entries.sortedBy { it.title.lowercase() }
    SpeedDialSort.NAME_DESCENDING -> entries.sortedByDescending { it.title.lowercase() }
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
