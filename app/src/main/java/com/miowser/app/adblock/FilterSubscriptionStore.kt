package com.miowser.app.adblock

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val PREFS_NAME = "adblock_filters"
private const val KEY_SUBSCRIPTIONS = "subscriptions"
private const val FIELD_DELIMITER = '\t'
private const val RECORD_DELIMITER = '\n'

const val EASYLIST_ID = "easylist"
const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
const val EASYPRIVACY_ID = "easyprivacy"
const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
const val PETER_LOWE_ID = "peter-lowe"
const val PETER_LOWE_URL = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&mimetype=plaintext&showintro=0"

data class FilterSubscription(
    val id: String,
    val name: String,
    val url: String,
    val isBuiltIn: Boolean,
    val enabled: Boolean,
    val lastUpdatedAt: Long = 0L,
    val lastError: String? = null,
    val networkRuleCount: Int = 0,
    val cosmeticRuleCount: Int = 0
)

object FilterSubscriptionCodec {
    private const val FIELD_COUNT = 9

    fun encode(subscriptions: List<FilterSubscription>): String =
        subscriptions.joinToString(RECORD_DELIMITER.toString()) { subscription ->
            listOf(
                sanitize(subscription.id),
                sanitize(subscription.name),
                sanitize(subscription.url),
                subscription.isBuiltIn.toString(),
                subscription.enabled.toString(),
                subscription.lastUpdatedAt.toString(),
                sanitize(subscription.lastError.orEmpty()),
                subscription.networkRuleCount.toString(),
                subscription.cosmeticRuleCount.toString()
            ).joinToString(FIELD_DELIMITER.toString())
        }

    fun decode(raw: String): List<FilterSubscription> =
        raw.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): FilterSubscription? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != FIELD_COUNT) return null
        return runCatching {
            FilterSubscription(
                id = parts[0],
                name = parts[1],
                url = parts[2],
                isBuiltIn = parts[3].toBooleanStrict(),
                enabled = parts[4].toBooleanStrict(),
                lastUpdatedAt = parts[5].toLong(),
                lastError = parts[6].ifBlank { null },
                networkRuleCount = parts[7].toInt(),
                cosmeticRuleCount = parts[8].toInt()
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.url.isNotBlank() }
    }

    private fun sanitize(value: String): String =
        value.replace(FIELD_DELIMITER, ' ').replace(RECORD_DELIMITER, ' ')
}

/**
 * Persists lightweight subscription metadata in SharedPreferences and the potentially large
 * filter-list bodies as streamed private ZIP files (with legacy text support). Keeping large
 * rule sets out of preferences avoids repeatedly serializing a multi-megabyte value every
 * time one row's enabled state changes.
 */
object FilterSubscriptionStore {
    internal val inputRevision = FilterInputRevision()
    private const val DIRECTORY_NAME = "adblock"
    private const val MANUAL_RULES_FILE = "manual-rules.txt"

    fun loadSubscriptions(context: Context): List<FilterSubscription> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUBSCRIPTIONS, null)
        val saved = raw?.let(FilterSubscriptionCodec::decode).orEmpty()
        val defaults = defaultSubscriptions(saved)
        val defaultIds = defaults.mapTo(HashSet()) { it.id }
        return defaults + saved.filterNot { it.id in defaultIds || it.isBuiltIn }
    }

    internal fun defaultSubscriptions(saved: List<FilterSubscription>): List<FilterSubscription> =
        listOf(
            Triple(PETER_LOWE_ID, "Peter Lowe's ad and tracking servers", PETER_LOWE_URL),
            Triple(EASYLIST_ID, "EasyList", EASYLIST_URL),
            Triple(EASYPRIVACY_ID, "EasyPrivacy", EASYPRIVACY_URL)
        ).map { (id, name, url) ->
            // A changed built-in source must refresh even if its old URL updated recently.
            val previous = saved.firstOrNull { it.id == id && it.url == url }
            FilterSubscription(id, previous?.name ?: name, url, isBuiltIn = true, enabled = true,
                lastUpdatedAt = previous?.lastUpdatedAt ?: 0L,
                lastError = previous?.lastError,
                networkRuleCount = previous?.networkRuleCount ?: 0,
                cosmeticRuleCount = previous?.cosmeticRuleCount ?: 0)
        }

    fun saveSubscriptions(context: Context, subscriptions: List<FilterSubscription>) {
        com.miowser.app.persistence.RestoreWriteFence.write {
            inputRevision.write { saveSubscriptionsUnfenced(context, subscriptions) }
        }
    }
    private fun saveSubscriptionsUnfenced(context: Context, subscriptions: List<FilterSubscription>) {
        check(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUBSCRIPTIONS, FilterSubscriptionCodec.encode(subscriptions))
            .commit()) { "Could not save filter subscriptions" }
    }

    fun readCachedList(context: Context, id: String): String? =
        cachedListFile(context, id)?.let { FilterListFiles.reader(it).use { reader -> reader.readText() } }

    internal fun cachedListFile(context: Context, id: String): File? =
        lists(context).existing(id)

    internal fun openCachedList(file: File): java.io.BufferedReader = FilterListFiles.reader(file)

    internal fun manualRulesFile(context: Context): File? =
        File(directory(context), MANUAL_RULES_FILE).takeIf(File::isFile)?.also {
            check(it.length() <= FilterLimits.LIST_BYTES) { "Saved custom rules exceed the 32 MB read limit" }
        }

    /** Compare a downloaded body without loading the old multi-megabyte copy into the heap. */
    internal fun cachedContentEquals(context: Context, id: String, content: String): Boolean {
        val file = cachedListFile(context, id) ?: return false
        return runCatching {
            openCachedList(file).use { reader ->
                val buffer = CharArray(8 * 1024)
                var offset = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (offset + count > content.length) return@use false
                    for (index in 0 until count) {
                        if (content[offset + index] != buffer[index]) return@use false
                    }
                    offset += count
                }
                offset == content.length
            }
        }.getOrDefault(false)
    }

    /** An unchanged ZIP is retained; a legacy source migrates on its next successful refresh. */
    internal fun cachedFileEquals(context: Context, id: String, candidate: File): Boolean =
        cachedListFile(context, id)?.extension == "zip" && lists(context).contentEquals(id, candidate)

    fun writeCachedList(context: Context, id: String, content: String) {
        com.miowser.app.persistence.RestoreWriteFence.write {
            inputRevision.write { writeCachedListUnfenced(context, id, content) }
        }
    }
    /** Atomically install a validated staged download without creating a full String copy. */
    internal fun writeCachedListFromFile(context: Context, id: String, source: File, checkActive: () -> Unit = {}) {
        require(source.isFile && source.length() <= FilterLimits.LIST_BYTES) { "List exceeds the 32 MB safety limit" }
        com.miowser.app.persistence.RestoreWriteFence.write {
            inputRevision.write { lists(context).install(id, { source.inputStream().buffered() }, checkActive) }
        }
    }
    private fun writeCachedListUnfenced(context: Context, id: String, content: String) {
        val parent = directory(context)
        check(parent.isDirectory || parent.mkdirs()) { "Could not create filter directory" }
        val staged = File.createTempFile("source-", ".tmp", parent)
        try {
            staged.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            lists(context).install(id, { staged.inputStream().buffered() })
        } finally { staged.delete() }
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Could not create filter directory" }
        val previousModified = target.lastModified()
        val temporary = File.createTempFile(target.name, ".tmp", parent)
        try {
            java.io.FileOutputStream(temporary).use { output ->
                java.io.OutputStreamWriter(output, Charsets.UTF_8).buffered().apply {
                    write(content)
                    flush()
                }
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target.setLastModified(maxOf(System.currentTimeMillis(), previousModified + 1))
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun deleteCachedList(context: Context, id: String) {
        com.miowser.app.persistence.RestoreWriteFence.write {
            inputRevision.write { deleteCachedListUnfenced(context, id) }
        }
    }
    private fun deleteCachedListUnfenced(context: Context, id: String) {
        lists(context).delete(id)
    }

    fun loadManualRules(context: Context): String =
        File(directory(context), MANUAL_RULES_FILE).takeIf(File::isFile)?.let {
            // Preserve previously accepted imports; the smaller budget applies to new saves.
            check(it.length() <= FilterLimits.LIST_BYTES) { "Saved custom rules exceed the 32 MB read limit" }
            it.readText()
        }.orEmpty()

    fun saveManualRules(context: Context, rules: String) {
        com.miowser.app.persistence.RestoreWriteFence.write {
            inputRevision.write { saveManualRulesUnfenced(context, rules) }
        }
    }
    private fun saveManualRulesUnfenced(context: Context, rules: String) {
        writeAtomically(File(directory(context), MANUAL_RULES_FILE), rules)
    }

    internal fun cachedBytes(context: Context, id: String): Long = cachedListFile(context, id)?.length() ?: 0L

    internal fun fingerprint(context: Context, rows: List<FilterSubscription>): String {
        val manual = File(directory(context), MANUAL_RULES_FILE)
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val data = buildString {
            append("engine-schema-3|").append(installed)
            rows.filter { it.enabled }.forEach {
                val file = cachedListFile(context, it.id)
                append('|').append(it.id).append(':').append(file?.length() ?: 0L).append(':').append(file?.lastModified() ?: 0L)
            }
            append("|manual:").append(manual.length()).append(':').append(manual.lastModified())
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun lists(context: Context) = FilterListFiles(directory(context))

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)
}
