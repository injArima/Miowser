package com.miowser.app.adblock

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.miowser.app.network.ConnectionBudget
import com.miowser.app.network.NativeConnections
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_FILTER_LIST_BYTES = FilterLimits.LIST_BYTES
private const val PERIODIC_WORK_NAME = "adblock-filter-refresh"

data class RefreshResult(val succeeded: Int, val failed: Int, val applied: Boolean = true) {
    val message: String
        get() = when {
            !applied -> "Lists processed; updated protection was not applied. Previous protection retained."
            failed == 0 -> if (succeeded == 1) "Filter list updated" else "$succeeded filter lists updated"
            succeeded == 0 -> "Filter-list refresh failed"
            else -> "$succeeded updated, $failed failed"
        }
}

internal data class FilterInputSource(val source: FilterSource, val label: String,
                                    val id: String, val url: String? = null,
                                    val open: () -> java.io.BufferedReader)

enum class FilterListStage { QUEUED, DOWNLOADING, CHECKING, WAITING_TO_APPLY, APPLYING, READY, FAILED }

data class FilterListProgress(
    val stage: FilterListStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null
)

/**
 * User-facing filter-list state plus the background refresh pipeline. Default protection is
 * deliberately immutable while the blocker is enabled: the three default lists have no row-level
 * switch or delete path, and the tiny curated fallback is compiled into the app. User subscriptions can
 * be independently enabled, refreshed, or removed.
 */
object AdBlockFilters {
    val subscriptions = mutableStateListOf<FilterSubscription>()
    val listProgress = mutableStateMapOf<String, FilterListProgress>()
    private val progressSession = AtomicLong()
    var manualRules by mutableStateOf("")
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isRefreshingAll by mutableStateOf(false)
        private set
    var isAddingLists by mutableStateOf(false)
        private set
    var bulkStatus by mutableStateOf<String?>(null)
        private set

    private val executor = Executors.newSingleThreadExecutor { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "filter-storage")
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val storageLock = Any()
    private val refreshLock = Any()
    @Volatile private var storageRevision = 0L
    @Volatile private var initialized = false
    @Volatile private var manualRefreshPending = false

    private fun beginListProgress(rows: List<FilterSubscription>): Long {
        val session = progressSession.incrementAndGet()
        mainHandler.post {
            if (progressSession.get() == session) {
                listProgress.clear()
                rows.forEach { listProgress[it.id] = FilterListProgress(FilterListStage.QUEUED) }
            }
        }
        return session
    }

    private fun updateListProgress(session: Long?, id: String, progress: FilterListProgress) {
        if (session == null) return
        mainHandler.post {
            if (progressSession.get() == session && id in listProgress) listProgress[id] = progress
        }
    }

    private fun failPendingProgress(session: Long?, message: String) {
        if (session == null) return
        mainHandler.post {
            if (progressSession.get() == session) listProgress.toMap().forEach { (id, progress) ->
                if (progress.stage !in setOf(FilterListStage.READY, FilterListStage.FAILED))
                    listProgress[id] = progress.copy(stage = FilterListStage.FAILED, error = message)
            }
        }
    }

    /** Browser entry reads storage off-main and publishes Compose state on its UI owner. */
    suspend fun initializeAsync(context: Context) {
        com.miowser.app.persistence.Persistence.requireOwner()
        SiteProtection.load(context)
        if (initialized) {
            if (com.miowser.app.settings.AppSettings.current.adBlockEnabled) AdBlock.engine.start(context)
            executor.execute { schedulePeriodicRefresh(context.applicationContext) }
            return
        }
        val appContext = context.applicationContext
        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(storageLock) {
                restoreAutomaticallyPausedLists(appContext) to
                    runCatching { FilterSubscriptionStore.loadManualRules(appContext) }.getOrDefault("")
            }
        }
        // Another entry point may have initialized the process while the read was pending.
        if (!initialized) {
            subscriptions.replaceWith(saved.first)
            manualRules = saved.second
            initialized = true
        }
        if (com.miowser.app.settings.AppSettings.current.adBlockEnabled) AdBlock.engine.start(appContext)
        executor.execute { schedulePeriodicRefresh(appContext) }
    }

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
        if (isAddingLists || isRefreshing) { onComplete("Wait for the current list operation to finish"); return }
        if (subscriptions.count { !it.isBuiltIn } >= FilterLimits.SAVED_CUSTOM_LISTS) {
            onComplete("Maximum 50 saved lists. Remove an unused list first"); return
        }
        val normalizedUrl = FilterSubscriptionUrls.normalize(url)
        if (normalizedUrl == null) {
            onComplete("Enter a valid HTTP or HTTPS filter-list URL")
            return
        }
        val appContext = context.applicationContext
        val existing = subscriptions.any { FilterSubscriptionUrls.normalize(it.url) == normalizedUrl }
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
        isAddingLists = true
        persistMutation(appContext, "Could not save subscription", onError = {
            isAddingLists = false
            onComplete(it)
        }, action = {
            val current = FilterSubscriptionStore.loadSubscriptions(appContext)
            check(current.none { FilterSubscriptionUrls.normalize(it.url) == normalizedUrl }) { "That filter list is already added" }
            check(current.count { !it.isBuiltIn } < FilterLimits.SAVED_CUSTOM_LISTS) { "Maximum 50 saved lists" }
            saveSubscriptions(appContext, current + subscription)
        }, onCommitted = {
            isAddingLists = false
            refreshOne(appContext, subscription.id, onComplete)
        })
    }

    /** One metadata commit and one rebuild per batch; downloads stay off the UI thread. */
    fun addSubscriptions(context: Context, pastedUrls: String, onComplete: (String) -> Unit) {
        com.miowser.app.persistence.Persistence.requireOwner()
        if (isAddingLists || isRefreshing) { onComplete("Wait for the current list operation to finish"); return }
        if (pastedUrls.length > 100_000) { onComplete("Paste up to 50 filter-list URLs at a time"); return }
        val inputs = pastedUrls.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (inputs.isEmpty() || inputs.size > 50) { onComplete("Paste between 1 and 50 filter-list URLs"); return }
        val app = context.applicationContext
        isAddingLists = true
        manualRefreshPending = true
        bulkStatus = "Adding filter lists…"
        executor.execute {
            var batchSession: Long? = null
            val result = runCatching {
                var overLimit = 0
                var duplicates = 0
                var invalid = 0
                val added = synchronized(storageLock) {
                    val current = FilterSubscriptionStore.loadSubscriptions(app)
                    var savedSlots = (FilterLimits.SAVED_CUSTOM_LISTS - current.count { !it.isBuiltIn }).coerceAtLeast(0)
                    val selection = FilterSubscriptionUrls.selectNew(current.map { it.url }, inputs)
                    duplicates = selection.duplicates
                    invalid = selection.invalid
                    val rows = selection.urls.mapNotNull { url ->
                        if (savedSlots <= 0) { overLimit++; null }
                        else {
                            savedSlots--
                            val uri = URI(url)
                            val file = uri.path.orEmpty().substringAfterLast('/').take(64)
                            FilterSubscription(UUID.randomUUID().toString(),
                                if (file.isBlank()) uri.host else "${uri.host} / $file",
                                url, isBuiltIn = false, enabled = true)
                        }
                    }
                    if (rows.isNotEmpty()) saveSubscriptions(app, current + rows)
                    rows
                }
                val saved = readObservableState(app)
                mainHandler.post { saved?.let(::publishObservableState) }
                val session = beginListProgress(added).also { batchSession = it }
                val summary = "${added.size} saved and enabled; $duplicates duplicates, $invalid invalid URLs, $overLimit over the 50-list limit skipped"
                if (added.isEmpty()) summary else {
                    val refreshed = refreshBlocking(app, added.map { it.id }.toSet(),
                        onProgress = { done, total -> mainHandler.post {
                            bulkStatus = if (done == total) "Applying filter lists…"
                                else "Downloading lists: $done of $total complete"
                        } }, progressSession = session)
                    "$summary. ${refreshed.message}" +
                        if (refreshed.failed > 0) ". Failed lists are saved for retry." else ""
                }
            }
            val saved = readObservableState(app)
            mainHandler.post {
                saved?.let(::publishObservableState)
                isAddingLists = false
                manualRefreshPending = false
                val message = result.getOrElse { "Could not finish adding lists: ${conciseError(it)}. Saved lists can be refreshed." }
                if (result.isFailure) failPendingProgress(batchSession, message)
                bulkStatus = message
                onComplete(message)
            }
        }
    }

    /** A display-name edit does not change cached rules or trigger a network request. */
    fun rename(context: Context, id: String, name: String, onComplete: (String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > 80 || trimmed.any { it.isISOControl() }) {
            onComplete("Enter a name of up to 80 characters")
            return
        }
        val app = context.applicationContext
        persistMutation(app, "Could not rename filter list", onComplete, action = {
            val current = FilterSubscriptionStore.loadSubscriptions(app)
            check(current.any { it.id == id }) { "List no longer exists" }
            saveSubscriptions(app, current.map { row ->
                if (row.id == id) row.copy(name = trimmed) else row
            })
        }, onCommitted = { onComplete(null) })
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean, onApplied: (String?) -> Unit = {}) {
        val app = context.applicationContext
        if (subscriptions.none { it.id == id && !it.isBuiltIn }) return
        var needsRefresh = false
        persistMutation(app, "Could not save the filter selection", onApplied, action = {
            val current = FilterSubscriptionStore.loadSubscriptions(app)
            saveSubscriptions(app, current.map { row ->
                if (row.id == id && !row.isBuiltIn) row.copy(enabled = enabled,
                    lastError = row.lastError?.takeUnless { it.startsWith("Paused for browser responsiveness:") }) else row
            })
            needsRefresh = enabled && FilterSubscriptionStore.cachedBytes(app, id) == 0L
        }, onCommitted = {
            if (needsRefresh) executor.execute {
                val applied = runCatching { refreshBlocking(app, setOf(id)).applied }.getOrDefault(false)
                val observable = readObservableState(app)
                mainHandler.post {
                    observable?.let(::publishObservableState)
                    onApplied(if (applied) null else "Protection update not applied; installed rules retained")
                }
            } else reloadAfterMutation(app, onApplied)
        })
    }

    fun remove(context: Context, id: String, onRemoved: () -> Unit = {}, onApplied: (String?) -> Unit = {}) {
        val app = context.applicationContext
        if (subscriptions.none { it.id == id && !it.isBuiltIn }) return
        persistMutation(app, "Could not remove filter list", onApplied, action = {
            saveSubscriptions(app, FilterSubscriptionStore.loadSubscriptions(app)
                .filterNot { it.id == id && !it.isBuiltIn })
        }, onCommitted = {
            onRemoved()
            reloadAfterMutation(app, onApplied)
        })
    }

    /** Undo is ordered after removal, and the cached body remains until snackbar dismissal. */
    fun restore(context: Context, subscription: FilterSubscription, onApplied: (String?) -> Unit = {}) {
        if (subscription.isBuiltIn) return
        val app = context.applicationContext
        persistMutation(app, "Could not restore filter list", onApplied, action = {
            val current = FilterSubscriptionStore.loadSubscriptions(app)
            if (current.none { it.id == subscription.id }) saveSubscriptions(app, current + subscription)
        }, onCommitted = { reloadAfterMutation(app, onApplied) })
    }

    fun discardRemoved(context: Context, id: String) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("filter-cache-delete") {
            synchronized(storageLock) {
                if (FilterSubscriptionStore.loadSubscriptions(app).none { it.id == id })
                    FilterSubscriptionStore.deleteCachedList(app, id)
            }
            true
        }
    }

    fun saveManualRules(context: Context, rules: String, onComplete: (String) -> Unit) {
        val app = context.applicationContext
        if (rules.length > FilterLimits.MANUAL_BYTES) {
            onComplete("Custom rules must fit within 256 KB. Use a subscription for larger lists"); return
        }
        val normalized = rules.trim()
        var count = 0
        var skipped = 0
        persistMutation(app, "Could not save custom rules", onComplete, action = {
            check(normalized.toByteArray(Charsets.UTF_8).size <= FilterLimits.MANUAL_BYTES) {
                "Custom rules must fit within 256 KB. Use a subscription for larger lists"
            }
            val parsed = normalized.reader().buffered().use { FilterListParser.scan(it, FilterSource.MANUAL,
                acceptLine = HashSet<String>()::add) }
            FilterSubscriptionStore.saveManualRules(app, normalized)
            storageRevision++
            count = parsed.networkRules + parsed.cosmeticRules
            skipped = parsed.skippedRegex + parsed.skippedMalformed + parsed.skippedUnsupported
        }, onCommitted = {
            AdBlock.engine.reloadAsync(app) { applied -> mainHandler.post {
                onComplete(if (!applied) "Rules saved; updated protection was not applied. Previous protection retained."
                    else "$count custom rule${if (count == 1) "" else "s"} applied" +
                        if (skipped > 0) "; $skipped unsupported or invalid skipped" else "")
            } }
        })
    }

    fun refreshAll(context: Context, onComplete: (String) -> Unit) {
        if (isRefreshing || isAddingLists) { onComplete("Wait for the current list operation to finish"); return }
        isRefreshing = true
        isRefreshingAll = true
        manualRefreshPending = true
        val appContext = context.applicationContext
        val session = beginListProgress(subscriptions.filter { it.enabled })
        executor.execute {
            val result = runCatching { refreshBlocking(appContext, null, progressSession = session) }
            val observable = readObservableState(appContext)
            mainHandler.post {
                observable?.let(::publishObservableState)
                isRefreshing = false
                isRefreshingAll = false
                manualRefreshPending = false
                if (result.isFailure) failPendingProgress(session, conciseError(result.exceptionOrNull()!!))
                onComplete(result.fold({ it.message }, { "Filter refresh failed: ${conciseError(it)}" }))
            }
        }
    }

    fun refreshOne(context: Context, id: String, onComplete: (String) -> Unit) {
        if (isRefreshing || isAddingLists) { onComplete("Wait for the current list operation to finish"); return }
        if (subscriptions.none { it.id == id && it.enabled }) { onComplete("Enable this list before downloading it"); return }
        isRefreshing = true
        manualRefreshPending = true
        val appContext = context.applicationContext
        val session = beginListProgress(subscriptions.filter { it.id == id })
        executor.execute {
            val result = runCatching { refreshBlocking(appContext, setOf(id), progressSession = session) }
            val observable = readObservableState(appContext)
            mainHandler.post {
                observable?.let(::publishObservableState)
                isRefreshing = false
                manualRefreshPending = false
                if (result.isFailure) failPendingProgress(session, conciseError(result.exceptionOrNull()!!))
                onComplete(result.fold({ it.message }, { "Filter refresh failed: ${conciseError(it)}" }))
            }
        }
    }

    /** Called by WorkManager too, including when no activity or Compose state exists. */
    fun refreshAllBlocking(
        context: Context,
        checkActive: () -> Unit = {},
        onConnection: (HttpURLConnection?) -> Unit = {},
        onlyIds: Set<String>? = null
    ): RefreshResult = refreshBlocking(context, onlyIds,
        checkActive = {
            if (manualRefreshPending) throw java.util.concurrent.CancellationException("Manual list refresh has priority")
            checkActive()
        }, onConnection = onConnection)

    internal fun fingerprint(context: Context): String = synchronized(storageLock) {
        FilterSubscriptionStore.fingerprint(context, restoreAutomaticallyPausedLists(context))
    }

    internal fun stableInputs(context: Context): Pair<Long, String> {
        repeat(3) {
            FilterSubscriptionStore.inputRevision.readStable { fingerprint(context) }?.let { return it }
        }
        error("Filter inputs are changing; previous protection retained")
    }

    /** One-time repair of switches changed by the previous automatic pause policy. */
    private fun restoreAutomaticallyPausedLists(context: Context): List<FilterSubscription> {
        val original = FilterSubscriptionStore.loadSubscriptions(context)
        val prefs = context.getSharedPreferences("adblock_filters", Context.MODE_PRIVATE)
        if (prefs.getBoolean("automatic_pause_policy_removed", false)) return original
        val restored = original.map { row ->
            if (!row.enabled && row.lastError == "Paused for browser responsiveness: active-list, size or rule budget exceeded")
                row.copy(enabled = true, lastError = null)
            else row
        }
        if (restored != original) saveSubscriptions(context, restored)
        check(prefs.edit().putBoolean("automatic_pause_policy_removed", true).commit()) { "Could not save filter migration" }
        return restored
    }

    /** Readers are opened and closed by the consumer; no complete source String is retained. */
    internal fun forEachEnabledSource(context: Context, consume: (FilterInputSource) -> Unit) {
        val rows = synchronized(storageLock) { restoreAutomaticallyPausedLists(context) }
        rows.filter { it.isBuiltIn && it.enabled }.forEach { row ->
            val cached = runCatching { FilterSubscriptionStore.cachedListFile(context, row.id) }.getOrNull()
            if (cached != null) consume(FilterInputSource(FilterSource.SUBSCRIBED, row.name, row.id, row.url) { FilterSubscriptionStore.openCachedList(cached) })
            else if (row.id == EASYLIST_ID) consume(FilterInputSource(FilterSource.SUBSCRIBED, row.name, row.id, row.url) {
                context.assets.open("easylist.txt").bufferedReader()
            })
        }
        // Manual rules are small and explicitly authored; give them space before optional lists.
        FilterSubscriptionStore.manualRulesFile(context)?.let { manual ->
            consume(FilterInputSource(FilterSource.MANUAL, "Custom rules", "custom-rules") { manual.bufferedReader() })
        }
        rows.filter { !it.isBuiltIn && it.enabled }.forEach { row ->
            FilterSubscriptionStore.cachedListFile(context, row.id)?.let { cached ->
                consume(FilterInputSource(FilterSource.SUBSCRIBED, row.name, row.id, row.url) { FilterSubscriptionStore.openCachedList(cached) })
            }
        }
    }

    private fun refreshBlocking(
        context: Context,
        onlyIds: Set<String>?,
        checkActive: () -> Unit = {},
        onConnection: (HttpURLConnection?) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        progressSession: Long? = null
    ): RefreshResult = synchronized(refreshLock) {
        var succeeded = 0
        var failed = 0
        val rowsAtStart = synchronized(storageLock) {
            restoreAutomaticallyPausedLists(context)
        }
        val before = fingerprint(context)
        val eligible = rowsAtStart.filter { it.enabled && (onlyIds == null || it.id in onlyIds) }
            .sortedWith(compareBy<FilterSubscription> {
                when {
                    it.lastUpdatedAt == 0L || it.lastError != null -> 0
                    else -> 1
                }
            }.thenBy { it.lastUpdatedAt })
        onProgress(0, eligible.size)
        val queue = ArrayDeque(eligible)
        val refreshedById = LinkedHashMap<String, FilterSubscription>()
        while (queue.isNotEmpty()) {
            val subscription = queue.removeFirst()
            checkActive()
            var downloadedBytes = 0L
            var totalBytes: Long? = null
            updateListProgress(progressSession, subscription.id, FilterListProgress(FilterListStage.DOWNLOADING))
            val updated = runCatching {
                downloadAndCache(context, subscription, checkActive, onConnection,
                    onDownloadProgress = { bytes, total ->
                        downloadedBytes = bytes
                        totalBytes = total
                        updateListProgress(progressSession, subscription.id,
                            FilterListProgress(FilterListStage.DOWNLOADING, bytes, total))
                    },
                    onChecking = {
                        updateListProgress(progressSession, subscription.id,
                            FilterListProgress(FilterListStage.CHECKING, downloadedBytes, totalBytes))
                    })
            }
                .fold(
                    onSuccess = { updated ->
                        succeeded++
                        updateListProgress(progressSession, subscription.id,
                            FilterListProgress(FilterListStage.WAITING_TO_APPLY, downloadedBytes, totalBytes))
                        updated
                    },
                    onFailure = { error ->
                        failed++
                        updateListProgress(progressSession, subscription.id,
                            FilterListProgress(FilterListStage.FAILED, downloadedBytes, totalBytes, conciseError(error)))
                        subscription.copy(lastError = conciseError(error))
                    }
                )
            checkActive()
            refreshedById[subscription.id] = updated
            onProgress(refreshedById.size, eligible.size)
        }
        // Merge into the latest metadata rather than the snapshot from before the network calls:
        // a user may enable, disable, add, or remove another list while a slow refresh is running.
        synchronized(storageLock) {
            FilterSubscriptionStore.loadSubscriptions(context).map { latest ->
                refreshedById[latest.id]?.copy(enabled = latest.enabled, name = latest.name) ?: latest
            }.also { saveSubscriptions(context, it) }
        }
        eligible.filter { refreshedById[it.id]?.lastError == null }.forEach {
            updateListProgress(progressSession, it.id,
                FilterListProgress(FilterListStage.APPLYING))
        }
        val applied = if (before == fingerprint(context) && AdBlock.engine.isFullyLoaded) true
            else AdBlock.engine.reloadNow(context)
        eligible.filter { refreshedById[it.id]?.lastError == null }.forEach {
            updateListProgress(progressSession, it.id,
                FilterListProgress(if (applied) FilterListStage.READY else FilterListStage.FAILED,
                    error = if (applied) null else "Downloaded, but protection was not applied"))
        }
        if (initialized) {
            val observable = readObservableState(context)
            mainHandler.post { observable?.let(::publishObservableState) }
        }
        RefreshResult(succeeded, failed, applied)
    }

    private fun downloadAndCache(context: Context, subscription: FilterSubscription,
                                 checkActive: () -> Unit, onConnection: (HttpURLConnection?) -> Unit,
                                 onDownloadProgress: (Long, Long?) -> Unit = { _, _ -> },
                                 onChecking: () -> Unit = {}): FilterSubscription {
        val connection = NativeConnections.openPublic(URL(subscription.url),
            ConnectionBudget.Priority.PREFETCH,
            { checkActive(); if (Thread.currentThread().isInterrupted) throw InterruptedException() },
            onOpen = onConnection) {
            it.connectTimeout = CONNECT_TIMEOUT_MS
            it.readTimeout = READ_TIMEOUT_MS
            it.setRequestProperty("User-Agent", "Miowser/0.1 filter-list updater")
            it.setRequestProperty("Accept", "text/plain, */*;q=0.1")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) error("HTTP $responseCode")
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_FILTER_LIST_BYTES) error("List is larger than 32 MB")
            val totalBytes = declaredLength.takeIf { it > 0 }
            onDownloadProgress(0, totalBytes)
            val stagingDirectory = File(context.cacheDir, "filter-staging")
            check(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) { "Could not create filter staging directory" }
            val staged = File.createTempFile("list-", ".download", stagingDirectory)
            try {
                connection.inputStream.use { input ->
                    staged.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastReportBytes = 0L
                        var lastReportAt = android.os.SystemClock.elapsedRealtime()
                        val deadline = lastReportAt + TimeUnit.SECONDS.toMillis(45)
                        while (true) {
                            checkActive()
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            check(android.os.SystemClock.elapsedRealtime() < deadline) { "Filter download took too long" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FILTER_LIST_BYTES) error("List is larger than 32 MB")
                            output.write(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (total - lastReportBytes >= 256 * 1024 || now - lastReportAt >= 150) {
                                onDownloadProgress(total, totalBytes)
                                lastReportBytes = total
                                lastReportAt = now
                            }
                        }
                        output.flush()
                        onDownloadProgress(total, totalBytes)
                    }
                }
                onChecking()
                val preview = staged.bufferedReader(Charsets.UTF_8).use { reader ->
                    CharArray(512).let { buffer ->
                        val count = reader.read(buffer)
                        if (count <= 0) "" else String(buffer, 0, count).trimStart()
                    }
                }
                val hostsSource = subscription.id == PETER_LOWE_ID &&
                    staged.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.any { it.trimStart().startsWith("127.0.0.1 ") } }
                check(!preview.startsWith("<") &&
                    (!connection.contentType.orEmpty().contains("text/html", ignoreCase = true) || hostsSource)) {
                    "Server returned HTML instead of a filter list"
                }
                // Count/validate one rule at a time. Never retain a second parsed ruleset just
                // to validate a download while the installed protection is already in memory.
                val parsed = staged.bufferedReader(Charsets.UTF_8).use { reader ->
                    FilterListParser.scan(reader, FilterSource.SUBSCRIBED, acceptLine = HashSet<String>()::add)
                }
                if (parsed.networkRules == 0 && parsed.cosmeticRules == 0) {
                    error("No supported filter rules found")
                }
                // Replace the active source only after the staged file parsed successfully.
                if (!FilterSubscriptionStore.cachedFileEquals(context, subscription.id, staged))
                    FilterSubscriptionStore.writeCachedListFromFile(context, subscription.id, staged, checkActive)
                synchronized(storageLock) {
                    if (FilterSubscriptionStore.loadSubscriptions(context).none { it.id == subscription.id })
                        FilterSubscriptionStore.deleteCachedList(context, subscription.id)
                }
                return subscription.copy(
                    lastUpdatedAt = System.currentTimeMillis(),
                    lastError = null,
                    networkRuleCount = parsed.networkRules,
                    cosmeticRuleCount = parsed.cosmeticRules
                )
            } finally {
                staged.delete()
            }
        } finally {
            onConnection(null)
            NativeConnections.close(connection)
        }
    }

    private data class ObservableFilters(val revision: Long, val rows: List<FilterSubscription>, val manual: String)

    private fun saveSubscriptions(context: Context, rows: List<FilterSubscription>) {
        // All callers hold storageLock; increment only after a successful commit.
        FilterSubscriptionStore.saveSubscriptions(context, rows)
        storageRevision++
    }

    private fun readObservableState(context: Context): ObservableFilters? = runCatching {
        synchronized(storageLock) {
            ObservableFilters(storageRevision, FilterSubscriptionStore.loadSubscriptions(context),
                FilterSubscriptionStore.loadManualRules(context))
        }
    }.getOrNull()

    private fun publishObservableState(saved: ObservableFilters) {
        // No disk-shared monitor on Main. A newer committed edit suppresses an older callback.
        if (saved.revision == storageRevision) {
            subscriptions.replaceWith(saved.rows)
            manualRules = saved.manual
        }
    }

    private fun persistMutation(context: Context, errorMessage: String, onError: (String) -> Unit,
        action: () -> Unit, onCommitted: () -> Unit) {
        com.miowser.app.persistence.Persistence.requireOwner()
        var observable: ObservableFilters? = null
        var failure = errorMessage
        com.miowser.app.persistence.Persistence.submit("filter-configuration") {
            try {
                synchronized(storageLock) { action(); observable = readObservableState(context) }
                true
            } catch (error: Exception) {
                failure = "$errorMessage: ${conciseError(error)}"
                throw error
            }
        }.thenAccept { saved -> mainHandler.post {
            if (!saved) onError(failure) else {
                observable?.let(::publishObservableState)
                onCommitted()
            }
        } }
    }

    private fun reloadAfterMutation(context: Context, onApplied: (String?) -> Unit) {
        val expectedRevision = storageRevision
        AdBlock.engine.reloadAsync(context) { applied -> mainHandler.post {
            // A newer edit owns its own reload outcome; superseded requests are not errors.
            onApplied(if (applied || storageRevision != expectedRevision) null
                else "Protection update not applied; installed rules retained")
        } }
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            FilterListRefreshWorker::class.java,
            4,
            TimeUnit.DAYS
        ).setConstraints(constraints).setInitialDelay(4, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        val staleIds = synchronized(storageLock) {
            FilterSubscriptionStore.loadSubscriptions(context).filter {
                it.enabled && (it.lastUpdatedAt == 0L || it.lastError != null ||
                    System.currentTimeMillis() - it.lastUpdatedAt >= TimeUnit.DAYS.toMillis(4))
            }.map { it.id }
        }
        val prefs = context.getSharedPreferences("adblock_filters", Context.MODE_PRIVATE)
        // Retire the old catch-up job once; it refreshed every list when only one was stale.
        if (!prefs.getBoolean("catch_up_v2_retired", false)) {
            WorkManager.getInstance(context).cancelUniqueWork("adblock-catch-up-refresh-v2")
            prefs.edit().putBoolean("catch_up_v2_retired", true).apply()
        }
        if (staleIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val lastQueued = prefs.getLong("catch_up_queued_at", 0L)
        if (now - lastQueued in 0 until TimeUnit.HOURS.toMillis(6)) return
        val catchUpRequest = androidx.work.OneTimeWorkRequest.Builder(FilterListRefreshWorker::class.java)
            .setConstraints(constraints)
            .setInputData(androidx.work.Data.Builder()
                .putStringArray("subscription_ids", staleIds.toTypedArray())
                .putLong("queued_at", now).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "adblock-catch-up-refresh-v3", androidx.work.ExistingWorkPolicy.KEEP, catchUpRequest)
        prefs.edit().putLong("catch_up_queued_at", now).apply()
    }

    private fun conciseError(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName).replace('\n', ' ').take(160)

    private fun <T> MutableList<T>.replaceWith(items: List<T>) {
        clear()
        addAll(items)
    }
}
