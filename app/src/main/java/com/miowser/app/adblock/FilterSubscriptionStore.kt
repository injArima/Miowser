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
 * filter-list bodies as private files. Keeping EasyList's ~70k rules out of preferences avoids
 * repeatedly serializing a multi-megabyte value every time one row's enabled state changes.
 */
object FilterSubscriptionStore {
    private const val DIRECTORY_NAME = "adblock"
    private const val MANUAL_RULES_FILE = "manual-rules.txt"

    fun loadSubscriptions(context: Context): List<FilterSubscription> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SUBSCRIPTIONS, null)
        val saved = raw?.let(FilterSubscriptionCodec::decode).orEmpty()
        val savedEasyList = saved.firstOrNull { it.id == EASYLIST_ID }
        val builtIn = FilterSubscription(
            id = EASYLIST_ID,
            name = "EasyList",
            url = EASYLIST_URL,
            isBuiltIn = true,
            enabled = true,
            lastUpdatedAt = savedEasyList?.lastUpdatedAt ?: 0L,
            lastError = savedEasyList?.lastError,
            networkRuleCount = savedEasyList?.networkRuleCount ?: 0,
            cosmeticRuleCount = savedEasyList?.cosmeticRuleCount ?: 0
        )
        return listOf(builtIn) + saved.filterNot { it.id == EASYLIST_ID || it.isBuiltIn }
    }

    fun saveSubscriptions(context: Context, subscriptions: List<FilterSubscription>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUBSCRIPTIONS, FilterSubscriptionCodec.encode(subscriptions))
            .commit()
    }

    fun readCachedList(context: Context, id: String): String? =
        cacheFile(context, id).takeIf(File::isFile)?.readText()

    fun writeCachedList(context: Context, id: String, content: String) {
        val target = cacheFile(context, id)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(content)
        try {
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
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun deleteCachedList(context: Context, id: String) {
        cacheFile(context, id).delete()
    }

    fun loadManualRules(context: Context): String =
        File(directory(context), MANUAL_RULES_FILE).takeIf(File::isFile)?.readText().orEmpty()

    fun saveManualRules(context: Context, rules: String) {
        val directory = directory(context).apply { mkdirs() }
        File(directory, MANUAL_RULES_FILE).writeText(rules)
    }

    private fun cacheFile(context: Context, id: String): File {
        val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        require(safeId.isNotBlank()) { "Invalid subscription id" }
        return File(directory(context), "list-$safeId.txt")
    }

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)
}
