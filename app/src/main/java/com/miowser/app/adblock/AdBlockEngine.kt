package com.miowser.app.adblock

import android.content.Context
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
        var readyStatus = "Saved filter selection active"
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
    @Volatile private var installedFingerprint: String? = null
    private val publicationLock = Any()
    @Volatile private var state = stateWithCuratedFallback().also { it.origins.freeze() }
    /** Installed saved selection, with an EasyList baseline when selected; missing downloads are reported. */
    @Volatile var isFullyLoaded: Boolean = false
        private set
    @Volatile var lastReloadError: String? = null
        private set

    fun loadEasyListInBackground(context: Context) = reloadInBackground(context)

    private val readiness = FilterReadiness({ isFullyLoaded }, { requestReload(checkNotNull(startupContext)) }, { lastReloadError })
    @Volatile private var startupContext: Context? = null

    /** Start saved restoration off-main; no network refresh is part of readiness. */
    fun start(context: Context) {
        if (isFullyLoaded) return
        startupContext = context.applicationContext
        readiness.start()
    }

    /** A usable saved generation is required before the first protected navigation. */
    suspend fun awaitReady(context: Context) {
        if (isFullyLoaded || !com.miowser.app.settings.AppSettings.current.adBlockEnabled) return
        startupContext = context.applicationContext
        try {
            readiness.awaitWhileEnabled(androidx.compose.runtime.snapshotFlow {
                com.miowser.app.settings.AppSettings.current.adBlockEnabled
            })
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (!isFullyLoaded) {
                lastReloadError = error.message ?: "Saved filter loading failed; retry browser preparation"
                loadStatus.value = lastReloadError!!
            }
            throw error
        }
    }

    private class Reload(val inputs: Pair<Long, String>)
    private val preparing = Executors.newSingleThreadExecutor { Thread(it, "adblock-inputs") }
    private val builds = SharedFilterBuilds<Pair<Long, String>>(reloadExecutor)

    private fun requestReload(context: Context): java.util.concurrent.CompletableFuture<Boolean> {
        val receipt = java.util.concurrent.CompletableFuture<Boolean>()
        val app = context.applicationContext
        preparing.execute {
            try {
                val inputs = AdBlockFilters.stableInputs(app)
                builds.submit(inputs) { rebuild(app, Reload(inputs)) }.whenComplete { value, error ->
                    if (error != null) receipt.completeExceptionally(error) else receipt.complete(value)
                }
            } catch (error: OutOfMemoryError) {
                lastReloadError = "Not enough memory to read saved filter inputs; retry browser preparation"
                loadStatus.value = lastReloadError!!
                receipt.completeExceptionally(IllegalStateException(lastReloadError, error))
            } catch (error: Exception) {
                lastReloadError = error.message ?: "Could not read saved filter inputs"
                loadStatus.value = lastReloadError!!
                receipt.complete(false)
            }
        }
        return receipt
    }

    fun reloadInBackground(context: Context) {
        requestReload(context)
    }

    internal fun reloadAsync(context: Context, onComplete: (Boolean) -> Unit) {
        requestReload(context).whenComplete { loaded, error -> onComplete(error == null && loaded == true) }
    }

    /** Off-main refresh receipt. False means superseded inputs or a failed candidate, never loss of installed rules. */
    internal fun reloadNow(context: Context): Boolean {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { "Filter reload must run off-main" }
        return requestReload(context).get()
    }

    private fun rebuild(context: Context, reload: Reload): Boolean {
        lastReloadError = null
        loadStatus.value = if (isFullyLoaded) "Updating lists; installed protection remains active" else "Basic protection active; loading saved filter cache"
        var builtFingerprint: String? = null
        var needsSnapshot = false
        val built = try {
            FilterMemoryGuard().check()
            val fingerprint = reload.inputs.second
            if (FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) { true } != true) return false
            if (AdBlockFilters.fingerprint(context) != fingerprint) return false
            if (fingerprint == installedFingerprint && isFullyLoaded) {
                loadStatus.value = state.readyStatus
                return FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) { true } == true
            }
            builtFingerprint = fingerprint
            val rows = FilterSubscriptionStore.loadSubscriptions(context).filter { it.enabled }
            val plan = SavedFilterPlan(rows.mapTo(HashSet()) { it.id }, rows.mapNotNullTo(HashSet()) { row ->
                runCatching { FilterSubscriptionStore.cachedListFile(context, row.id) }.getOrNull()?.let { row.id }
            })
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
                buildState(context, plan).also { needsSnapshot = true }
            }.also { it.readyStatus = plan.status(it.skippedSources) }
        } catch (error: OutOfMemoryError) {
            // Last-resort containment. Normal builds stop at the headroom checks before OOM.
            synchronized(publicationLock) {
                lastReloadError = "Not enough memory to update filter lists; previous protection retained"
                loadStatus.value = lastReloadError!!
            }
            return false
        } catch (error: Exception) {
            synchronized(publicationLock) {
                lastReloadError = if (error is FilterMemoryPressureException) error.message
                    else "${error.message ?: "Filter rebuild failed"}; previous protection retained"
                loadStatus.value = lastReloadError!!
            }
            com.miowser.app.diagnostics.SafeLog.error(TAG, "Filter rebuild failed; keeping the installed ruleset", error)
            return false
        }
        if (AdBlockFilters.fingerprint(context) != builtFingerprint) return false
        val applied = FilterSubscriptionStore.inputRevision.ifCurrent(reload.inputs.first) { synchronized(publicationLock) {
            state = built
            installedFingerprint = builtFingerprint
            isFullyLoaded = true
            lastReloadError = null
            loadStatus.value = built.readyStatus
            revision.value = revision.value + 1
            true
        } } == true
        // Activate the rules before paying disk serialization cost. Cache writes are optional.
        if (applied) reloadExecutor.execute {
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

    private fun buildState(context: Context, plan: SavedFilterPlan): EngineState = buildSavedFilterCandidate(
        hasWorkingGeneration = { isFullyLoaded },
        onSkipped = { error -> com.miowser.app.diagnostics.SafeLog.warning(TAG,
            "Skipping saved source ${error.id}; rebuilding with bundled baseline", error) }
    ) { excluded -> buildState(context, plan, excluded) }

    private fun buildState(context: Context, plan: SavedFilterPlan, excluded: Set<String>): EngineState {
        val built = stateWithCuratedFallback()
        built.skippedSources = excluded.size
        var baselineLoaded = false
        var networkCount = CuratedFallbackList.RULES.size
        var cosmeticCount = 0
        var skipped = 0
        val accumulator = FilterRuleAccumulator(built.networkEngine, built.origins)
        val cosmeticsSeen = HashSet<CosmeticRule>().apply { addAll(built.cosmeticGeneric) }
        cosmeticCount = cosmeticsSeen.size
        AdBlockFilters.forEachEnabledSource(context) { source ->
            if (source.source != FilterSource.MANUAL && source.id !in plan.enabledIds) return@forEachEnabledSource
            if (source.id in excluded && source.id != EASYLIST_ID) return@forEachEnabledSource
            val origin = built.origins.register(FilterRuleSource(source.id, source.label, source.url))
            val parsed = try {
                (if (source.id == EASYLIST_ID && source.id in excluded)
                    context.assets.open("easylist.txt").bufferedReader() else source.open()).use { reader ->
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
                catch (error: Exception) {
                    throw UnreadableSavedFilter(source.id, error)
                }
            if (source.id == EASYLIST_ID) {
                if (parsed.networkRules == 0) throw UnreadableSavedFilter(source.id, IllegalStateException("EasyList baseline is empty"))
                baselineLoaded = true
            }
            skipped += parsed.skippedRegex + parsed.skippedMalformed + parsed.skippedUnsupported
        }
        check(!plan.requiresEasyList || baselineLoaded) { "Saved filter generation has no usable EasyList baseline" }
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

    // Disposable cache. Format/parser/content changes invalidate it; compatible APK updates do not.
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
