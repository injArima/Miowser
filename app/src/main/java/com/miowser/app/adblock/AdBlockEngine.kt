package com.miowser.app.adblock

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import java.net.URI

private const val TAG = "Miowser/AdBlockEngine"

/**
 * Public façade the browser layer talks to. Loads the curated fallback list synchronously
 * (tiny, instant) and the bundled EasyList asset (~78k rules) on a background thread, then
 * answers block/allow + cosmetic-CSS queries. Requests made before EasyList finishes loading
 * are still checked against the curated list, so ad-blocking isn't fully off during the
 * roughly one-time startup parse.
 */
class AdBlockEngine {
    private val networkEngine = FilterEngine()
    private val cosmeticByDomain = HashMap<String, MutableList<CosmeticEntry>>()
    private val cosmeticGeneric = mutableListOf<CosmeticEntry>()
    private val cosmeticExceptions = HashSet<String>()

    /** A selector plus the domains it's carved out on (see CosmeticRule.excludedDomains) — usually empty. */
    private data class CosmeticEntry(val selector: String, val excludedDomains: List<String>)

    @Volatile private var loadingStarted = false
    @Volatile var isFullyLoaded: Boolean = false
        private set

    init {
        for (raw in CuratedFallbackList.RULES) {
            runCatching { FilterListParser.parseNetworkRule(raw, FilterSource.SUBSCRIBED) }
                .onSuccess { networkEngine.addRule(it) }
        }
    }

    fun loadEasyListInBackground(context: Context) {
        if (loadingStarted) return
        loadingStarted = true
        Thread({ loadEasyList(context.applicationContext) }, "AdBlockEngine-Load")
            .apply { isDaemon = true }
            .start()
    }

    private fun loadEasyList(context: Context) {
        val start = System.currentTimeMillis()
        try {
            val text = context.assets.open("easylist.txt").bufferedReader().use { it.readText() }
            val result = FilterListParser.parse(text, FilterSource.SUBSCRIBED)
            result.networkRules.forEach(networkEngine::addRule)
            result.cosmeticRules.forEach(::addCosmeticRule)
            val elapsed = System.currentTimeMillis() - start
            Log.i(
                TAG,
                "loaded ${result.networkRules.size} network rules, ${result.cosmeticRules.size} cosmetic rules " +
                    "(${result.skippedRegex} regex + ${result.skippedMalformed} malformed skipped) in ${elapsed}ms"
            )
        } catch (e: Exception) {
            Log.e(TAG, "failed to load bundled easylist.txt — ad-blocking limited to curated fallback list", e)
        } finally {
            isFullyLoaded = true
        }
    }

    // internal, not private: unit tests add cosmetic rules directly rather than through the full
    // loadEasyList(Context) path, which needs a real asset file and Android Context neither are
    // available to a plain JVM unit test.
    internal fun addCosmeticRule(rule: CosmeticRule) {
        if (rule.isException) {
            cosmeticExceptions += "${rule.domain}|${rule.selector}"
            return
        }
        val entry = CosmeticEntry(rule.selector, rule.excludedDomains)
        if (rule.domain == null) {
            cosmeticGeneric += entry
        } else {
            cosmeticByDomain.getOrPut(rule.domain) { mutableListOf() } += entry
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

        return when (val decision = networkEngine.classify(requestUrl, requestHost, pageHost, resourceType)) {
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
        val selectors = LinkedHashSet<String>()
        selectors += applicableSelectors(cosmeticGeneric, hostname)
        var h = hostname
        while (true) {
            cosmeticByDomain[h]?.let { selectors += applicableSelectors(it, hostname) }
            val dot = h.indexOf('.')
            if (dot == -1) break
            h = h.substring(dot + 1)
        }
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(",\n") + " { display: none !important; }"
    }

    // Exclusion is checked against the real page hostname, not against whichever domain-hierarchy
    // level (h, in cosmeticCssFor's walk) happened to match the rule's own inclusion domain —
    // "example.com,~sub.example.com##sel" must not hide on sub.example.com even though "sub." is
    // never itself a key this rule is stored under (it's stored under "example.com").
    private fun applicableSelectors(entries: List<CosmeticEntry>, hostname: String): List<String> =
        entries
            .filterNot { "$hostname|${it.selector}" in cosmeticExceptions }
            .filterNot { entry -> entry.excludedDomains.any { hostnameMatchesDomain(hostname, it) } }
            .map { it.selector }

    private fun hostnameOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()
}
