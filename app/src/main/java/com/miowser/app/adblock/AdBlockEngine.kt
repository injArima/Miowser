package com.miowser.app.adblock

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "Miowser/AdBlockEngine"

/**
 * WebView's ad-blocking facade.
 * Every source is parsed into a fresh state and then published in one atomic swap, so requests
 * see a complete old or new ruleset while lists change. The small curated fallback exists
 * synchronously before the larger subscribed sources finish parsing.
 */
class AdBlockEngine {
    /** A selector plus the domains it's carved out on (see CosmeticRule.excludedDomains) - usually empty. */
    private data class CosmeticEntry(val selector: String, val excludedDomains: List<String>)

    private data class EngineState(
        val networkEngine: FilterEngine = FilterEngine(),
        val cosmeticByDomain: HashMap<String, MutableList<CosmeticEntry>> = HashMap(),
        val cosmeticGeneric: MutableList<CosmeticEntry> = mutableListOf(),
        val cosmeticExceptions: HashSet<String> = HashSet()
    )

    private val reloadExecutor = Executors.newSingleThreadExecutor()
    private val requestedGeneration = AtomicLong(0)
    @Volatile private var state = stateWithCuratedFallback()
    @Volatile var isFullyLoaded: Boolean = false
        private set

    /** Kept as a compatibility entry point for older callers. */
    fun loadEasyListInBackground(context: Context) = reloadInBackground(context)

    fun reloadInBackground(context: Context) {
        val generation = requestedGeneration.incrementAndGet()
        isFullyLoaded = false
        reloadExecutor.execute {
            val built = buildState(context.applicationContext)
            if (generation == requestedGeneration.get()) {
                state = built
                isFullyLoaded = true
            }
        }
    }

    /** WorkManager already owns a background thread, so it can rebuild synchronously. */
    internal fun reloadNow(context: Context) {
        requestedGeneration.incrementAndGet()
        state = buildState(context.applicationContext)
        isFullyLoaded = true
    }

    private fun buildState(context: Context): EngineState {
        val start = System.currentTimeMillis()
        val built = stateWithCuratedFallback()
        var networkCount = CuratedFallbackList.RULES.size
        var cosmeticCount = 0
        var skippedRegex = 0
        var skippedMalformed = 0
        try {
            AdBlockFilters.enabledSources(context).forEach { source ->
                val result = FilterListParser.parse(source.text, source.source)
                result.networkRules.forEach(built.networkEngine::addRule)
                result.cosmeticRules.forEach { addCosmeticRule(built, it) }
                networkCount += result.networkRules.size
                cosmeticCount += result.cosmeticRules.size
                skippedRegex += result.skippedRegex
                skippedMalformed += result.skippedMalformed
            }
            Log.i(
                TAG,
                "loaded $networkCount network rules, $cosmeticCount cosmetic rules " +
                    "($skippedRegex regex + $skippedMalformed malformed skipped) in " +
                    "${System.currentTimeMillis() - start}ms"
            )
        } catch (error: Exception) {
            Log.e(TAG, "failed to rebuild filters; keeping curated fallback protection", error)
        }
        return built
    }

    private fun stateWithCuratedFallback(): EngineState = EngineState().also { built ->
        CuratedFallbackList.RULES.forEach { raw ->
            runCatching { FilterListParser.parseNetworkRule(raw, FilterSource.SUBSCRIBED) }
                .onSuccess(built.networkEngine::addRule)
        }
        // A recurring native-ad rail seen in the wild. The child marker keeps this narrower
        // than hiding every unrelated component that happens to use the generic fh-wrapper name.
        addCosmeticRule(
            built,
            CosmeticRule(
                domain = null,
                selector = ".fh-wrapper:has(.fh-ad-label)",
                isException = false,
                source = FilterSource.SUBSCRIBED
            )
        )
    }

    // internal, not private: unit tests add cosmetic rules directly rather than through the full
    // loadEasyList(Context) path, which needs a real asset file and Android Context neither are
    // available to a plain JVM unit test.
    internal fun addCosmeticRule(rule: CosmeticRule) {
        addCosmeticRule(state, rule)
    }

    private fun addCosmeticRule(target: EngineState, rule: CosmeticRule) {
        if (rule.isException) {
            target.cosmeticExceptions += "${rule.domain}|${rule.selector}"
            return
        }
        val entry = CosmeticEntry(rule.selector, rule.excludedDomains)
        if (rule.domain == null) {
            target.cosmeticGeneric += entry
        } else {
            target.cosmeticByDomain.getOrPut(rule.domain) { mutableListOf() } += entry
        }
    }

    /** Returns a stub response if this request should be blocked, or null to allow it through. */
    fun decide(
        pageUrl: String,
        requestUrl: String,
        headers: Map<String, String>,
        isMainFrame: Boolean
    ): WebResourceResponse? {
        val pageHost = hostnameOf(pageUrl) ?: return null
        val requestHost = hostnameOf(requestUrl) ?: return null
        val resourceType = ResourceType.infer(requestUrl, headers, isMainFrame)
        val current = state

        return when (val decision = current.networkEngine.classify(requestUrl, requestHost, pageHost, resourceType)) {
            is FilterDecision.Block -> {
                Log.d(TAG, "blocked ($resourceType): $requestUrl [${decision.rule.raw}]")
                StubResponses.forType(resourceType)
            }
            FilterDecision.Allow -> null
        }
    }

    /** CSS (or empty string) to inject for the given page hostname's cosmetic filters. */
    fun cosmeticCssFor(hostname: String): String {
        if (hostname.isBlank()) return ""
        val current = state
        val selectors = LinkedHashSet<String>()
        selectors += applicableSelectors(current, current.cosmeticGeneric, hostname)
        var h = hostname
        while (true) {
            current.cosmeticByDomain[h]?.let { selectors += applicableSelectors(current, it, hostname) }
            val dot = h.indexOf('.')
            if (dot == -1) break
            h = h.substring(dot + 1)
        }
        if (selectors.isEmpty()) return ""
        // Emit one rule per selector. In a comma-separated selector list, one unsupported or
        // malformed selector invalidates the entire block and can silently disable every other
        // cosmetic rule on that page.
        return selectors.joinToString("\n") { "$it { display: none !important; }" }
    }

    // Exclusion is checked against the real page hostname, not against whichever domain-hierarchy
    // level (h, in cosmeticCssFor's walk) happened to match the rule's own inclusion domain —
    // "example.com,~sub.example.com##sel" must not hide on sub.example.com even though "sub." is
    // never itself a key this rule is stored under (it's stored under "example.com").
    private fun applicableSelectors(
        current: EngineState,
        entries: List<CosmeticEntry>,
        hostname: String
    ): List<String> =
        entries
            .filterNot { "$hostname|${it.selector}" in current.cosmeticExceptions }
            .filterNot { entry -> entry.excludedDomains.any { hostnameMatchesDomain(hostname, it) } }
            .map { it.selector }

    private fun hostnameOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()
}
