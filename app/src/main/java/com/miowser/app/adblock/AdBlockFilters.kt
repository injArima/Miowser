package com.miowser.app.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_FILTER_LIST_BYTES = 20 * 1024 * 1024
private const val PERIODIC_WORK_NAME = "adblock-filter-refresh"

data class RefreshResult(val succeeded: Int, val failed: Int) {
    val message: String
        get() = when {
            failed == 0 -> if (succeeded == 1) "Filter list updated" else "$succeeded filter lists updated"
            succeeded == 0 -> "Filter-list refresh failed"
            else -> "$succeeded updated, $failed failed"
        }
}

internal data class FilterTextSource(val text: String, val source: FilterSource)

/**
 * User-facing filter-list state plus the background refresh pipeline. Default protection is
 * deliberately immutable while the blocker is enabled: EasyList has no row-level switch or
 * delete path, and the tiny curated fallback is compiled into the app. User subscriptions can
 * be independently enabled, refreshed, or removed.
 */
object AdBlockFilters {
    val subscriptions = mutableStateListOf<FilterSubscription>()
    var manualRules by mutableStateOf("")
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val storageLock = Any()
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(storageLock) {
            if (initialized) return
            val appContext = context.applicationContext
            subscriptions.replaceWith(FilterSubscriptionStore.loadSubscriptions(appContext))
            manualRules = FilterSubscriptionStore.loadManualRules(appContext)
            initialized = true
            AdBlock.engine.reloadInBackground(appContext)
            schedulePeriodicRefresh(appContext)
        }
    }

    fun addSubscription(
        context: Context,
        name: String,
        url: String,
        onComplete: (String) -> Unit
    ) {
        val normalizedUrl = normalizeUrl(url)
        if (normalizedUrl == null) {
            onComplete("Enter a valid HTTP or HTTPS filter-list URL")
            return
        }
        val appContext = context.applicationContext
        val existing = subscriptions.any { it.url.equals(normalizedUrl, ignoreCase = true) }
        if (existing) {
            onComplete("That filter list is already added")
            return
        }
        val displayName = name.trim().ifBlank {
            runCatching { URI(normalizedUrl).host }.getOrNull().orEmpty().ifBlank { "Custom list" }
        }
        val subscription = FilterSubscription(
            id = UUID.randomUUID().toString(),
            name = displayName,
            url = normalizedUrl,
            isBuiltIn = false,
            enabled = true
        )
        synchronized(storageLock) {
            val updated = FilterSubscriptionStore.loadSubscriptions(appContext) + subscription
            FilterSubscriptionStore.saveSubscriptions(appContext, updated)
        }
        subscriptions.add(subscription)
        refreshOne(context, subscription.id, onComplete)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean, onApplied: () -> Unit = {}) {
        val appContext = context.applicationContext
        val index = subscriptions.indexOfFirst { it.id == id && !it.isBuiltIn }
        if (index == -1) return
        val updatedRow = subscriptions[index].copy(enabled = enabled)
        subscriptions[index] = updatedRow
        synchronized(storageLock) {
            val updated = FilterSubscriptionStore.loadSubscriptions(appContext)
                .map { if (it.id == id && !it.isBuiltIn) it.copy(enabled = enabled) else it }
            FilterSubscriptionStore.saveSubscriptions(appContext, updated)
        }
        executor.execute {
            AdBlock.engine.reloadNow(appContext)
            mainHandler.post { onApplied() }
        }
    }

    fun remove(context: Context, id: String, onApplied: () -> Unit = {}) {
        val appContext = context.applicationContext
        val row = subscriptions.firstOrNull { it.id == id } ?: return
        if (row.isBuiltIn) return
        subscriptions.remove(row)
        synchronized(storageLock) {
            val updated = FilterSubscriptionStore.loadSubscriptions(appContext)
                .filterNot { it.id == id && !it.isBuiltIn }
            FilterSubscriptionStore.saveSubscriptions(appContext, updated)
        }
        executor.execute {
            AdBlock.engine.reloadNow(appContext)
            mainHandler.post { onApplied() }
        }
    }

    /** Restores a just-removed row while its cached body is retained for snackbar Undo. */
    fun restore(context: Context, subscription: FilterSubscription, onApplied: () -> Unit = {}) {
        if (subscription.isBuiltIn) return
        val appContext = context.applicationContext
        synchronized(storageLock) {
            val current = FilterSubscriptionStore.loadSubscriptions(appContext)
            if (current.none { it.id == subscription.id }) {
                FilterSubscriptionStore.saveSubscriptions(appContext, current + subscription)
            }
        }
        subscriptions.replaceWith(FilterSubscriptionStore.loadSubscriptions(appContext))
        executor.execute {
            AdBlock.engine.reloadNow(appContext)
            mainHandler.post { onApplied() }
        }
    }

    /** Permanently discards the retained cache once the removal snackbar is dismissed. */
    fun discardRemoved(context: Context, id: String) {
        synchronized(storageLock) {
            if (FilterSubscriptionStore.loadSubscriptions(context).none { it.id == id }) {
                FilterSubscriptionStore.deleteCachedList(context, id)
            }
        }
    }

    fun saveManualRules(context: Context, rules: String, onComplete: (String) -> Unit) {
        val appContext = context.applicationContext
        val normalized = rules.trim()
        executor.execute {
            synchronized(storageLock) {
                FilterSubscriptionStore.saveManualRules(appContext, normalized)
            }
            val parsed = FilterListParser.parse(normalized, FilterSource.MANUAL)
            AdBlock.engine.reloadNow(appContext)
            val count = parsed.networkRules.size + parsed.cosmeticRules.size
            mainHandler.post {
                manualRules = normalized
                onComplete(if (count == 1) "1 custom rule applied" else "$count custom rules applied")
            }
        }
    }

    fun refreshAll(context: Context, onComplete: (String) -> Unit) {
        if (isRefreshing) return
        isRefreshing = true
        val appContext = context.applicationContext
        executor.execute {
            val result = refreshAllBlocking(appContext)
            mainHandler.post {
                refreshObservableState(appContext)
                isRefreshing = false
                onComplete(result.message)
            }
        }
    }

    fun refreshOne(context: Context, id: String, onComplete: (String) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = refreshBlocking(appContext, setOf(id))
            mainHandler.post {
                refreshObservableState(appContext)
                onComplete(result.message)
            }
        }
    }

    /** Called by WorkManager too, including when no activity or Compose state exists. */
    fun refreshAllBlocking(context: Context): RefreshResult = refreshBlocking(context, null)

    internal fun enabledSources(context: Context): List<FilterTextSource> {
        val rows = synchronized(storageLock) { FilterSubscriptionStore.loadSubscriptions(context) }
        val result = mutableListOf<FilterTextSource>()
        val easyListText = synchronized(storageLock) {
            FilterSubscriptionStore.readCachedList(context, EASYLIST_ID)
        } ?: context.assets.open("easylist.txt").bufferedReader().use { it.readText() }
        result += FilterTextSource(easyListText, FilterSource.SUBSCRIBED)

        rows.asSequence()
            .filter { !it.isBuiltIn && it.enabled }
            .mapNotNull { row ->
                synchronized(storageLock) { FilterSubscriptionStore.readCachedList(context, row.id) }
                    ?.let { FilterTextSource(it, FilterSource.SUBSCRIBED) }
            }
            .forEach(result::add)

        val manual = synchronized(storageLock) { FilterSubscriptionStore.loadManualRules(context) }
        if (manual.isNotBlank()) result += FilterTextSource(manual, FilterSource.MANUAL)
        return result
    }

    private fun refreshBlocking(context: Context, onlyIds: Set<String>?): RefreshResult {
        var succeeded = 0
        var failed = 0
        val rowsAtStart = synchronized(storageLock) {
            FilterSubscriptionStore.loadSubscriptions(context)
        }
        val refreshedById = rowsAtStart.mapNotNull { subscription ->
            if (!subscription.enabled || (onlyIds != null && subscription.id !in onlyIds)) {
                return@mapNotNull null
            }
            subscription.id to runCatching { downloadAndCache(context, subscription) }
                .fold(
                    onSuccess = { updated ->
                        succeeded++
                        updated
                    },
                    onFailure = { error ->
                        failed++
                        subscription.copy(lastError = conciseError(error))
                    }
                )
        }.toMap()
        // Merge into the latest metadata rather than the snapshot from before the network calls:
        // a user may enable, disable, add, or remove another list while a slow refresh is running.
        val updatedRows = synchronized(storageLock) {
            FilterSubscriptionStore.loadSubscriptions(context).map { latest ->
                refreshedById[latest.id]?.copy(enabled = latest.enabled) ?: latest
            }.also { FilterSubscriptionStore.saveSubscriptions(context, it) }
        }
        AdBlock.engine.reloadNow(context)
        if (initialized) mainHandler.post { subscriptions.replaceWith(updatedRows) }
        return RefreshResult(succeeded, failed)
    }

    private fun downloadAndCache(context: Context, subscription: FilterSubscription): FilterSubscription {
        val connection = (URL(subscription.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Miowser/0.1 filter-list updater")
            setRequestProperty("Accept", "text/plain, */*;q=0.1")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) error("HTTP $responseCode")
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_FILTER_LIST_BYTES) error("List is larger than 20 MB")
            val body = connection.inputStream.use { input ->
                input.readTextUpTo(MAX_FILTER_LIST_BYTES)
            }
            val parsed = FilterListParser.parse(body, FilterSource.SUBSCRIBED)
            if (parsed.networkRules.isEmpty() && parsed.cosmeticRules.isEmpty()) {
                error("No supported filter rules found")
            }
            FilterSubscriptionStore.writeCachedList(context, subscription.id, body)
            return subscription.copy(
                lastUpdatedAt = System.currentTimeMillis(),
                lastError = null,
                networkRuleCount = parsed.networkRules.size,
                cosmeticRuleCount = parsed.cosmeticRules.size
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun refreshObservableState(context: Context) {
        subscriptions.replaceWith(
            synchronized(storageLock) { FilterSubscriptionStore.loadSubscriptions(context) }
        )
        manualRules = synchronized(storageLock) { FilterSubscriptionStore.loadManualRules(context) }
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            FilterListRefreshWorker::class.java,
            4,
            TimeUnit.DAYS
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun normalizeUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) null
        else uri.normalize().toString()
    }.getOrNull()

    private fun conciseError(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName).replace('\n', ' ').take(160)

    private fun InputStream.readTextUpTo(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) error("List is larger than 20 MB")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun <T> MutableList<T>.replaceWith(items: List<T>) {
        clear()
        addAll(items)
    }
}
