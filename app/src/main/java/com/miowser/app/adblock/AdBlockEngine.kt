package com.miowser.app.adblock

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import java.net.URI
import java.util.concurrent.Executors

private const val TAG = "Miowser/AdBlockEngine"

/** Whole generations are built off-main, then atomically published with their own caches. */
class AdBlockEngine {
    private data class DecisionKey(val page: String, val host: String, val url: String, val type: ResourceType)
    private class EngineState(
        val networkEngine: FilterEngine = FilterEngine(),
        val cosmeticByDomain: MutableMap<String, MutableList<CosmeticRule>> = HashMap(),
        val cosmeticGeneric: MutableList<CosmeticRule> = mutableListOf(),
        var skippedSources: Int = 0,
        val origins: FilterRuleOrigins = FilterRuleOrigins()
    ) {
        @Volatile var domainIndex: MappedDomainIndex? = null
        val decisions = BoundedFilterCache<DecisionKey, FilterDecision>(2_048, 1_048_576) { key, _ ->
            (key.page.length + key.host.length + key.url.length) * 2 + 64
        }
        val css = BoundedFilterCache<String, String>(128, 2_097_152) { key, value ->
            (key.length + value.length) * 2 + 32
        }
    }
    private val reloadExecutor = Executors.newSingleThreadExecutor { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "adblock-build")
    }
    val loadStatus = kotlinx.coroutines.flow.MutableStateFlow("Basic protection active; preparing filter lists")
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private var startupScheduled = false
    @Volatile private var installedFingerprint: String? = null
    private val publicationLock = Any()
    private var requestedGeneration = 0L
    @Volatile private var state = stateWithCuratedFallback().also { it.origins.freeze() }
    @Volatile var isFullyLoaded: Boolean = false
        private set
    @Volatile var lastReloadError: String? = null
        private set

    fun loadEasyListInBackground(context: Context) = reloadInBackground(context)

    /** Schedule once per process. The browser never waits for parsing or network updates. */
    fun start(context: Context) {
        synchronized(publicationLock) {
            if (startupScheduled || isFullyLoaded) return
            startupScheduled = true
            reloadInBackground(context)
        }
    }

    private class Reload(val inputs: Pair<Long, String>, @Volatile var generation: Long,
        val receipt: java.util.concurrent.CompletableFuture<Boolean> = java.util.concurrent.CompletableFuture())
    private val preparing = Executors.newSingleThreadExecutor { Thread(it, "adblock-inputs") }
    private val inFlight = mutableMapOf<Pair<Long, String>, Reload>()

    private fun requestReload(context: Context): java.util.concurrent.CompletableFuture<Boolean> {
        val generation = synchronized(publicationLock) { ++requestedGeneration }
        val receipt = java.util.concurrent.CompletableFuture<Boolean>()
        val app = context.applicationContext
        preparing.execute {
            try {
                val inputs = AdBlockFilters.stableInputs(app)
                val (reload, created) = synchronized(publicationLock) {
                    val existing = inFlight[inputs]
                    if (existing != null) {
                        existing.generation = maxOf(existing.generation, generation)
                        existing to false
                    } else Reload(inputs, generation).also { inFlight[inputs] = it } to true
                }
                reload.receipt.whenComplete { value, error ->
                    if (error != null) receipt.completeExceptionally(error) else receipt.complete(value)
                }
                if (created) reloadExecutor.execute {
                    val success = runCatching { rebuild(app, reload) }.getOrDefault(false)
                    synchronized(publicationLock) {
                        if (inFlight[inputs] === reload) inFlight.remove(inputs)
                    }
                    reload.receipt.complete(success)
                }
            } catch (_: Exception) { receipt.complete(false) }
        }
        return receipt
    }

    fun reloadInBackground(context: Context) {
        requestReload(context)
    }

    internal fun reloadAsync(context: Context, onComplete: (Boolean) -> Unit) {
        requestReload(context).whenComplete { loaded, error -> onComplete(error == null && loaded == true) }
    }

    /** Fingerprint preparation and parsing have different owners; callers never block either. */
    internal fun reloadNow(context: Context): Boolean = requestReload(context).get()

    private fun rebuild(context: Context, reload: Reload): Boolean {
        if (synchronized(publicationLock) { reload.generation != requestedGeneration }) return false
        loadStatus.value = if (isFullyLoaded) "Updating lists; installed protection remains active" else "Basic protection active; loading saved filter cache"
        var builtFingerprint: String? = null
        var needsSnapshot = false
        val built = try {
            FilterMemoryGuard().check()
            val fingerprint = reload.inputs.second
            if (FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) { true } != true) return false
            if (AdBlockFilters.fingerprint(context) != fingerprint) return false
            if (fingerprint == installedFingerprint && isFullyLoaded) {
                loadStatus.value = if (state.skippedSources == 0) "Filter lists active"
                    else "Protection active; ${state.skippedSources} filter list(s) skipped due to limits or errors"
                return FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) {
                    synchronized(publicationLock) { reload.generation == requestedGeneration }
                } == true
            }
            builtFingerprint = fingerprint
            val suffixStart = android.os.SystemClock.elapsedRealtime()
            PublicSuffixDomains.warmUp()
            com.miowser.app.diagnostics.SafeLog.debug(TAG,
                "Public suffix initialization: ${android.os.SystemClock.elapsedRealtime() - suffixStart} ms")
            val snapshotStart = android.os.SystemClock.elapsedRealtime()
            readSnapshot(context, fingerprint)?.also {
                com.miowser.app.diagnostics.SafeLog.debug(TAG,
                    "Loaded compiled filter snapshot in ${android.os.SystemClock.elapsedRealtime() - snapshotStart} ms")
            } ?: run {
                loadStatus.value = if (isFullyLoaded) "Rebuilding changed filter rules; installed protection remains active"
                    else "Basic protection active; building changed filter rules"
                buildState(context).also { needsSnapshot = true }
            }
        } catch (error: OutOfMemoryError) {
            // Last-resort containment. Normal builds stop at the headroom checks before OOM.
            synchronized(publicationLock) {
                if (reload.generation == requestedGeneration) {
                    startupScheduled = false
                    lastReloadError = "Not enough memory to update filter lists; previous protection retained"
                    loadStatus.value = lastReloadError!!
                }
            }
            return false
        } catch (error: Exception) {
            synchronized(publicationLock) {
                if (reload.generation == requestedGeneration) {
                    startupScheduled = false
                    lastReloadError = if (error is FilterMemoryPressureException) error.message
                        else "${error.message ?: "Filter rebuild failed"}; previous protection retained"
                    loadStatus.value = lastReloadError!!
                }
            }
            com.miowser.app.diagnostics.SafeLog.error(TAG, "Filter rebuild failed; keeping the installed ruleset", error)
            return false
        }
        if (AdBlockFilters.fingerprint(context) != builtFingerprint) return false
        val applied = FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) { synchronized(publicationLock) {
            if (reload.generation != requestedGeneration) false else {
                state = built
                installedFingerprint = builtFingerprint
                isFullyLoaded = true
                lastReloadError = null
                loadStatus.value = if (built.skippedSources == 0) "Filter lists active"
                    else "Protection active; ${built.skippedSources} filter list(s) skipped due to limits or errors"
                revision.value = revision.value + 1
                true
            }
        } } == true
        // Activate the rules before paying disk serialization cost. Cache writes are optional.
        if (applied) {
            val fingerprint = builtFingerprint
            if (AdBlockFilters.fingerprint(context) == fingerprint) {
                if (needsSnapshot) runCatching { writeSnapshot(context, fingerprint, built) }
                built.domainIndex = runCatching {
                    FilterMemoryGuard().check()
                    MappedDomainIndex.openOrBuild(java.io.File(context.cacheDir, "adblock"), fingerprint, built.networkEngine)
                }.getOrNull()
            }
        }
        return applied
    }

    private fun buildState(context: Context): EngineState {
        val built = stateWithCuratedFallback()
        var networkCount = CuratedFallbackList.RULES.size
        var cosmeticCount = 0
        var skipped = 0
        val accumulator = FilterRuleAccumulator(built.networkEngine, built.origins)
        val cosmeticsSeen = HashSet<CosmeticRule>().apply { addAll(built.cosmeticGeneric) }
        cosmeticCount = cosmeticsSeen.size
        AdBlockFilters.forEachEnabledSource(context) { source ->
            val origin = built.origins.register(FilterRuleSource(source.id, source.label, source.url))
            val parsed = try {
                source.open().use { reader ->
                    FilterListParser.scan(reader, source.source,
                        acceptLine = { line -> accumulator.accept(line, source.source, origin) },
                        onNetwork = { rule -> accumulator.add(rule, origin); networkCount++ },
                        onCosmetic = { rule ->
                            if (cosmeticsSeen.add(rule)) { addCosmeticRule(built, rule); cosmeticCount++ }
                        })
                }
            }
                catch (error: FilterMemoryPressureException) { throw error }
                // A bad archive can fail at EOF, after some rules were delivered. Never publish
                // that partial generation; reload retains the previously installed protection.
                catch (error: FilterSourceIntegrityException) { throw error }
                catch (error: Exception) {
                    built.skippedSources++
                    com.miowser.app.diagnostics.SafeLog.warning(TAG, "Skipping ${source.label}: ${error.message}")
                    return@forEachEnabledSource
                }
            skipped += parsed.skippedRegex + parsed.skippedMalformed + parsed.skippedUnsupported
        }
        // The frequency pass only needs final rule objects; release builder maps first.
        accumulator.release()
        cosmeticsSeen.clear()
        built.origins.freeze()
        if (!built.networkEngine.optimizeTokenIndexes()) {
            com.miowser.app.diagnostics.SafeLog.debug(TAG, "Token optimization stopped for memory headroom; compatible buckets retained")
        }
        com.miowser.app.diagnostics.SafeLog.info(TAG, "Prepared $networkCount network and $cosmeticCount cosmetic rules; $skipped unsupported/invalid rules skipped")
        return built
    }

    // Disposable, app-private cache. Schema/install/input changes invalidate it; corruption falls back to streamed sources.
    private fun readSnapshot(context: Context, fingerprint: String): EngineState? = try {
        val file = java.io.File(context.filesDir, "adblock/${CompiledFilterSnapshot.FILE_NAME}")
        if (!file.isFile) return null
        java.io.DataInputStream(java.io.BufferedInputStream(file.inputStream(), 256 * 1024)).use { input ->
            CompiledFilterSnapshot.read(input, fingerprint)?.let { saved ->
                EngineState(saved.network, saved.domains, saved.generic, saved.skippedSources, saved.origins)
            }
        }
    } catch (error: FilterMemoryPressureException) {
        throw error // Do not start a second build after a cache load stopped for memory pressure.
    } catch (error: Exception) {
        com.miowser.app.diagnostics.SafeLog.warning(TAG, "Compiled filter cache could not be read; rebuilding", error)
        null
    }

    private fun writeSnapshot(context: Context, fingerprint: String, built: EngineState) {
        val file = android.util.AtomicFile(java.io.File(context.filesDir, "adblock/${CompiledFilterSnapshot.FILE_NAME}"))
        var stream: java.io.FileOutputStream? = null
        try {
            file.baseFile.parentFile?.mkdirs()
            stream = file.startWrite()
            val output = java.io.DataOutputStream(java.io.BufferedOutputStream(stream, 256 * 1024))
            CompiledFilterSnapshot.write(output, fingerprint, built.networkEngine,
                built.cosmeticByDomain, built.cosmeticGeneric, built.skippedSources, built.origins)
            output.flush()
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(file::failWrite) // A cache failure must not prevent installing working filters.
            com.miowser.app.diagnostics.SafeLog.warning(TAG, "Compiled filter cache could not be saved", error)
        }
    }

    private fun stateWithCuratedFallback(): EngineState = EngineState().also { built ->
        val origin = built.origins.register(FilterRuleSource("miowser-built-in", "Miowser built-in protection"))
        CuratedFallbackList.RULES.forEach { raw ->
            built.networkEngine.addRule(FilterListParser.parseNetworkRule(raw, FilterSource.SUBSCRIBED)
                .also { it.originGroup = origin })
        }
        addCosmeticRule(built, CosmeticRule(null, ".fh-wrapper:has(.fh-ad-label)", false, FilterSource.SUBSCRIBED))
    }

    /** Test fixture hook: copy cosmetic collections so published readers remain immutable. */
    internal fun addCosmeticRule(rule: CosmeticRule) = synchronized(publicationLock) {
        val previous = state
        val next = EngineState(previous.networkEngine,
            previous.cosmeticByDomain.mapValues { it.value.toMutableList() }.toMutableMap(),
            previous.cosmeticGeneric.toMutableList(), origins = previous.origins)
        addCosmeticRule(next, rule)
        next.domainIndex = previous.domainIndex
        state = next
    }

    private fun addCosmeticRule(target: EngineState, rule: CosmeticRule) {
        if (rule.domain == null) target.cosmeticGeneric.add(rule)
        else target.cosmeticByDomain.getOrPut(rule.domain) { mutableListOf() }.add(rule)
    }

    fun decide(pageUrl: String, requestUrl: String, headers: Map<String, String>, isMainFrame: Boolean): WebResourceResponse? {
        if (isMainFrame) return blockedNavigation(requestUrl, headers)?.let {
            StubResponses.blockedDocument(it.rule, it.sources)
        }
        val pageHost = hostnameOf(pageUrl) ?: return null
        if (SiteProtection.mode(pageHost) == SiteProtectionMode.OFF) return null
        val requestHost = hostnameOf(requestUrl) ?: return null
        val type = ResourceType.infer(requestUrl, headers, isMainFrame)
        val current = state
        val decision = classify(current, pageHost, requestHost, requestUrl, type)
        // Responses contain streams, so cache decisions and create a fresh response per request.
        return if (decision is FilterDecision.Block) StubResponses.forType(type) else null
    }

    private fun classify(current: EngineState, pageHost: String, requestHost: String, requestUrl: String,
                         type: ResourceType): FilterDecision {
        val key = DecisionKey(pageHost, requestHost, requestUrl, type)
        return current.decisions.get(key) ?: current.networkEngine.classify(
            requestUrl, requestHost, pageHost, type, current.domainIndex?.candidateMask(requestHost)
        ).also {
            // Oversized/signed URLs still match normally, but do not monopolize cache memory.
            if (requestUrl.length <= 8_192) current.decisions.put(key, it)
        }
    }

    /** Derive source names from the installed generation, including lists pending removal. */
    fun blockedNavigation(url: String, headers: Map<String, String>): BlockedPageInfo? {
        val host = hostnameOf(url) ?: return null
        if (SiteProtection.mode(host) == SiteProtectionMode.OFF) return null
        val current = state
        val type = ResourceType.infer(url, headers, true)
        val decision = classify(current, host, host, url, type) as? FilterDecision.Block ?: return null
        val groups = current.networkEngine.blockingOriginGroups(url, host, host, type, decision)
        return BlockedPageInfo(url, decision.rule.raw, current.origins.sourcesFor(groups))
    }

    fun cosmeticCssFor(hostname: String): String {
        val host = canonicalHostname(hostname) ?: return ""
        val mode = SiteProtection.mode(host)
        if (mode == SiteProtectionMode.OFF) return ""
        val current = state
        val cacheKey = "$host|${mode.name}"
        current.css.get(cacheKey)?.let { return it }
        val winners = LinkedHashMap<String, CosmeticRule>()
        fun rank(rule: CosmeticRule): Int = (if (rule.source == FilterSource.MANUAL) 0 else 2) +
            (if (rule.isException) 0 else 1)
        fun consider(rules: List<CosmeticRule>) {
            for (rule in rules) {
                if (rule.excludedDomains.any { hostnameMatchesDomain(host, it) }) continue
                val old = winners[rule.selector]
                if (old == null || rank(rule) < rank(old)) winners[rule.selector] = rule
            }
        }
        consider(current.cosmeticGeneric)
        var suffix = host
        while (true) {
            current.cosmeticByDomain[suffix]?.let(::consider)
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
        // Global and parent-domain exceptions inherit to subdomains, with exclusions intact.
        val css = winners.values.filterNot { it.isException }
            .joinToString("\n") { "${it.selector} { display: none !important; }" }
        val strictSelectors = listOf("[data-ad-slot]", "[aria-label=\"Advertisement\"]", "[id^=\"google_ads_iframe\"]")
        val extra = if (mode == SiteProtectionMode.STRICT) strictSelectors
            .filter { winners[it]?.isException != true }
            .joinToString("\n") { "$it { display: none !important; }" } else ""
        val result = css + "\n" + extra
        current.css.put(cacheKey, result)
        return result
    }

    private fun hostnameOf(url: String): String? = runCatching { URI(url).host }
        .getOrNull()?.let(::canonicalHostname)
}
