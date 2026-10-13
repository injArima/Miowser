package com.miowser.app.library

import android.content.Context
import com.miowser.app.persistence.Persistence
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URI
import java.util.Base64
import java.util.UUID

data class LibraryFolder(
    val id: String,
    val name: String,
    val domain: String? = null,
    val parentId: String? = null,
    val additionalDomains: List<String> = emptyList(),
    val websiteOperator: LibraryRuleOperator = LibraryRuleOperator.ANY,
    val titleRule: LibraryTitleRule? = null
) {
    val websiteDomains: List<String> get() = (listOfNotNull(domain) + additionalDomains).distinct()
}

/** Accepts a website URL or a bare host; matching also includes its subdomains. */
fun libraryDomain(value: String?): String? {
    val input = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val url = if ("://" in input) input else "https://$input"
    return runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }
        .getOrNull()?.takeIf { it.isNotBlank() && !it.endsWith('.') }
}

fun libraryDomainMatches(pageUrl: String?, domain: String): Boolean {
    val host = libraryDomain(pageUrl) ?: return false
    return host == domain || host.endsWith(".$domain")
}

/** A small, versioned index. Folder contents remain in the existing Library records. */
object LibraryFolderCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private fun encoded(value: String): String = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decoded(value: String): String = String(decoder.decode(value), Charsets.UTF_8)

    private fun encodedTitleRule(rule: LibraryTitleRule?): String = rule?.let {
        encoded(listOf(it.source.name, it.operator.name, it.phrases.joinToString("\n", transform = ::encoded)).joinToString("\t"))
    }.orEmpty()

    private fun decodedTitleRule(value: String?): LibraryTitleRule? = value?.takeIf(String::isNotBlank)?.let { field ->
        val parts = decoded(field).split('\t', limit = 3)
        if (parts.size != 3) return@let null
        LibraryTitleRule(
            LibraryTitleSource.valueOf(parts[0]),
            LibraryRuleOperator.valueOf(parts[1]),
            parts[2].split('\n').filter(String::isNotBlank).map(::decoded)
        ).takeIf { it.phrases.isNotEmpty() }
    }

    fun encode(folders: List<LibraryFolder>): String = folders.joinToString("\n") { folder ->
        listOf(
            folder.id, encoded(folder.name), folder.domain ?: "", folder.parentId ?: "",
            encoded(folder.additionalDomains.joinToString("\n")),
            folder.websiteOperator.name, encodedTitleRule(folder.titleRule)
        ).joinToString("\t")
    }

    fun decode(raw: String): List<LibraryFolder> = raw.lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size !in 3..7 || fields[0].isBlank()) return@mapNotNull null
        runCatching {
            LibraryFolder(
                fields[0], decoded(fields[1]), fields[2].ifBlank { null },
                fields.getOrNull(3)?.ifBlank { null },
                fields.getOrNull(4)?.takeIf(String::isNotBlank)?.let(::decoded)?.split('\n')?.filter(String::isNotBlank).orEmpty(),
                fields.getOrNull(5)?.takeIf(String::isNotBlank)?.let(LibraryRuleOperator::valueOf) ?: LibraryRuleOperator.ANY,
                decodedTitleRule(fields.getOrNull(6))
            )
        }.getOrNull()
    }.filter { it.name.isNotBlank() }.toList().let(::validHierarchy)

    private fun validHierarchy(folders: List<LibraryFolder>): List<LibraryFolder> {
        val byId = folders.associateBy(LibraryFolder::id)
        return folders.map { folder ->
            val seen = mutableSetOf(folder.id)
            var parentId = folder.parentId
            var valid = true
            while (parentId != null) {
                if (!seen.add(parentId)) { valid = false; break }
                val parent = byId[parentId]
                if (parent == null) { valid = false; break }
                parentId = parent.parentId
            }
            if (valid) folder else folder.copy(parentId = null)
        }
    }
}

object LibraryFolders {
    private const val PREFS = "library_folders"
    private const val KEY = "folders"
    private const val DEFAULT_KEY = "default_folder_id"
    private val mutableRecords = mutableStateListOf<LibraryFolder>()
    val entries: List<LibraryFolder> get() = mutableRecords
    var defaultFolderId by mutableStateOf<String?>(null)
        private set
    var revision: Long = 0L
        private set
    private var compiled: CompiledLibraryRouter? = null
    fun router(): CompiledLibraryRouter = compiled ?: CompiledLibraryRouter(mutableRecords.toList(), defaultFolderId)
        .also { compiled = it }
    @Volatile private var restored = false

    suspend fun restoreAsync(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val (records, savedDefault) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            LibraryFolderCodec.decode(prefs.getString(KEY, "").orEmpty()) to prefs.getString(DEFAULT_KEY, null)
        }
        if (restored) return
        mutableRecords.addAll(records)
        defaultFolderId = savedDefault?.takeIf { id -> records.any { it.id == id && it.parentId == null } }
        compiled = null
        revision++
        restored = true
    }

    fun restore(context: Context) {
        Persistence.requireOwner()
        if (restored) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mutableRecords.addAll(LibraryFolderCodec.decode(prefs.getString(KEY, "").orEmpty()))
        defaultFolderId = prefs.getString(DEFAULT_KEY, null)?.takeIf { id -> mutableRecords.any { it.id == id && it.parentId == null } }
        compiled = null
        revision++
        restored = true
    }

    fun add(context: Context, name: String, domain: String? = null, parentId: String? = null): LibraryFolder? {
        Persistence.requireOwner()
        restore(context)
        val cleanName = name.trim().take(80)
        if (cleanName.isBlank() || (parentId != null && mutableRecords.none { it.id == parentId }) ||
            (domain != null && mutableRecords.any { domain in it.websiteDomains }) ||
            mutableRecords.any { it.parentId == parentId && it.name.equals(cleanName, ignoreCase = true) }) return null
        val folder = LibraryFolder(UUID.randomUUID().toString(), cleanName, domain, parentId)
        mutableRecords.add(folder)
        persist(context)
        return folder
    }

    fun rename(context: Context, id: String, name: String): Boolean {
        Persistence.requireOwner()
        val cleanName = name.trim().take(80)
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0 || cleanName.isBlank() || mutableRecords.any {
                it.id != id && it.parentId == mutableRecords[index].parentId && it.name.equals(cleanName, ignoreCase = true)
            }) return false
        mutableRecords[index] = mutableRecords[index].copy(name = cleanName)
        persist(context)
        return true
    }

    fun remove(context: Context, id: String) {
        Persistence.requireOwner()
        val folder = mutableRecords.firstOrNull { it.id == id } ?: return
        mutableRecords.remove(folder)
        mutableRecords.indices.forEach { index ->
            if (mutableRecords[index].parentId == id) {
                mutableRecords[index] = mutableRecords[index].copy(parentId = folder.parentId)
            }
        }
        if (defaultFolderId == id) defaultFolderId = folder.parentId
        persist(context)
    }

    fun addWebsiteRule(context: Context, id: String, value: String): Boolean {
        Persistence.requireOwner()
        restore(context)
        val domain = libraryDomain(value) ?: return false
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0 || mutableRecords[index].titleRule != null ||
            mutableRecords.any { it.id != id && domain in it.websiteDomains }) return false
        val folder = mutableRecords[index]
        if (domain in folder.websiteDomains) return false
        val domains = folder.websiteDomains + domain
        mutableRecords[index] = folder.copy(domain = domains.first(), additionalDomains = domains.drop(1))
        persist(context)
        return true
    }

    fun removeWebsiteRule(context: Context, id: String, domain: String) {
        Persistence.requireOwner()
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0) return
        val folder = mutableRecords[index]
        if (domain !in folder.websiteDomains) return
        val domains = folder.websiteDomains.filterNot { it == domain }
        mutableRecords[index] = folder.copy(domain = domains.firstOrNull(), additionalDomains = domains.drop(1))
        persist(context)
    }

    fun replaceWebsiteRule(context: Context, id: String, domains: List<String>, operator: LibraryRuleOperator): Boolean {
        Persistence.requireOwner()
        restore(context)
        val parsed = domains.map { libraryDomain(it) ?: return false }.distinct()
        if (parsed.any { domain -> mutableRecords.any { it.id != id && domain in it.websiteDomains } }) return false
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0) return false
        mutableRecords[index] = mutableRecords[index].copy(
            domain = parsed.firstOrNull(), additionalDomains = parsed.drop(1),
            websiteOperator = operator, titleRule = null
        )
        persist(context)
        return true
    }

    fun replaceTitleRule(context: Context, id: String, rule: LibraryTitleRule?): Boolean {
        Persistence.requireOwner()
        restore(context)
        if (rule != null && rule.phrases.any { it.isBlank() }) return false
        val index = mutableRecords.indexOfFirst { it.id == id }
        if (index < 0) return false
        mutableRecords[index] = mutableRecords[index].copy(
            domain = null, additionalDomains = emptyList(), titleRule = rule
        )
        persist(context)
        return true
    }

    fun setDefault(context: Context, id: String?) {
        Persistence.requireOwner()
        restore(context)
        if (id != null && mutableRecords.none { it.id == id && it.parentId == null }) return
        if (defaultFolderId == id) return
        defaultFolderId = id
        persist(context)
    }

    fun rootId(id: String?): String? {
        var folder = mutableRecords.firstOrNull { it.id == id } ?: return null
        val visited = mutableSetOf(folder.id)
        while (folder.parentId != null) {
            val parent = mutableRecords.firstOrNull { it.id == folder.parentId } ?: break
            if (!visited.add(parent.id)) break
            folder = parent
        }
        return folder.id
    }

    fun descendantsIncluding(id: String): Set<String> {
        val result = mutableSetOf(id)
        do {
            val before = result.size
            mutableRecords.forEach { folder ->
                if (folder.parentId in result) result.add(folder.id)
            }
        } while (result.size != before)
        return result
    }

    fun orderedEntries(): List<LibraryFolder> {
        val result = mutableListOf<LibraryFolder>()
        fun append(parentId: String?) {
            mutableRecords.filter { it.parentId == parentId }.forEach { folder ->
                result.add(folder)
                append(folder.id)
            }
        }
        append(null)
        return result
    }

    fun lineage(id: String): List<LibraryFolder> {
        val path = mutableListOf<LibraryFolder>()
        val visited = mutableSetOf<String>()
        var current = mutableRecords.firstOrNull { it.id == id }
        while (current != null && visited.add(current.id)) {
            path.add(current)
            current = mutableRecords.firstOrNull { it.id == current.parentId }
        }
        return path.asReversed()
    }

    fun path(folder: LibraryFolder): String = lineage(folder.id).joinToString(" / ") { it.name }

    fun matchingFolderId(context: Context, pageUrl: String?): String? {
        Persistence.requireOwner()
        restore(context)
        return chooseLibraryFolder(mutableRecords, pageUrl, null)
    }

    fun destinationFolderId(context: Context, pageUrl: String?): String? {
        Persistence.requireOwner()
        restore(context)
        return chooseLibraryFolder(mutableRecords, pageUrl, defaultFolderId)
    }

    fun routeNewVideo(context: Context, entry: LibraryEntry): String? {
        Persistence.requireOwner()
        restore(context)
        return router().route(defaultFolderId, entry.ruleFacts())
    }

    fun routeAfterTitleChange(context: Context, entry: LibraryEntry): String? {
        Persistence.requireOwner()
        restore(context)
        // Recheck the entire hierarchy so a changed title can leave its old parent,
        // match a different root, and then flow into that root's children.
        return router().route(defaultFolderId, entry.ruleFacts())
    }

    fun sourceForRule(context: Context, folderId: String): String? {
        Persistence.requireOwner()
        restore(context)
        return mutableRecords.firstOrNull { it.id == folderId }?.parentId ?: defaultFolderId
    }

    private fun persist(context: Context) {
        Persistence.requireOwner()
        revision++
        compiled = null
        val snapshot = mutableRecords.toList()
        val selectedDefault = defaultFolderId
        val appContext = context.applicationContext
        Persistence.submitSnapshot("library-folders") {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, LibraryFolderCodec.encode(snapshot))
                .putString(DEFAULT_KEY, selectedDefault)
                .commit()
        }
    }
}

/** Website-specific rules take precedence; the chosen default handles every other new video. */
fun chooseLibraryFolder(folders: List<LibraryFolder>, pageUrl: String?, defaultId: String?): String? =
    folders.asSequence()
        .filter { it.titleRule == null }
        .flatMap { folder -> folder.websiteDomains.asSequence().map { domain -> folder.id to domain } }
        .filter { (_, domain) -> libraryDomainMatches(pageUrl, domain) }
        .maxByOrNull { (_, domain) -> domain.length }?.first
        ?: defaultId?.takeIf { id -> folders.any { it.id == id } }
