package com.miowser.app.library

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.net.URI
import java.util.Base64
import java.util.UUID

data class LibraryFolder(val id: String, val name: String, val domain: String? = null)

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

    fun encode(folders: List<LibraryFolder>): String = folders.joinToString("\n") { folder ->
        listOf(folder.id, encoded(folder.name), folder.domain ?: "").joinToString("\t")
    }

    fun decode(raw: String): List<LibraryFolder> = raw.lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size != 3 || fields[0].isBlank()) return@mapNotNull null
        runCatching { LibraryFolder(fields[0], decoded(fields[1]), fields[2].ifBlank { null }) }.getOrNull()
    }.filter { it.name.isNotBlank() }.toList()
}

object LibraryFolders {
    private const val PREFS = "library_folders"
    private const val KEY = "folders"
    val entries = mutableStateListOf<LibraryFolder>()
    @Volatile private var restored = false

    fun restore(context: Context) {
        if (restored) return
        restored = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        entries.addAll(LibraryFolderCodec.decode(raw))
    }

    fun add(context: Context, name: String, domain: String? = null): LibraryFolder? {
        restore(context)
        val cleanName = name.trim().take(80)
        if (cleanName.isBlank() || entries.any { it.name.equals(cleanName, ignoreCase = true) }) return null
        val folder = LibraryFolder(UUID.randomUUID().toString(), cleanName, domain)
        entries.add(folder)
        persist(context)
        return folder
    }

    fun rename(context: Context, id: String, name: String): Boolean {
        val cleanName = name.trim().take(80)
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0 || cleanName.isBlank() || entries.any {
                it.id != id && it.name.equals(cleanName, ignoreCase = true)
            }) return false
        entries[index] = entries[index].copy(name = cleanName)
        persist(context)
        return true
    }

    fun remove(context: Context, id: String) {
        if (entries.removeAll { it.id == id }) persist(context)
    }

    fun matchingFolderId(context: Context, pageUrl: String?): String? {
        restore(context)
        return entries.asSequence()
            .filter { it.domain != null && libraryDomainMatches(pageUrl, it.domain) }
            .maxByOrNull { it.domain!!.length }?.id
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, LibraryFolderCodec.encode(entries)).apply()
    }
}
