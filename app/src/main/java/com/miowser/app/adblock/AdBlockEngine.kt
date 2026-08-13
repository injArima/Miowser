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
    private val cosmeticByDomain = HashMap<String, MutableList<String>>()
    private val cosmeticGeneric = mutableListOf<String>()
    private val cosmeticExceptions = HashSet<String>()

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

    private fun addCosmeticRule(rule: CosmeticRule) {
        if (rule.isException) {
            cosmeticExceptions += "${rule.domain}|${rule.selector}"
            return
        }
        if (rule.domain == null) {
            cosmeticGeneric += rule.selector
        } else {
            cosmeticByDomain.getOrPut(rule.domain) { mutableListOf() } += rule.selector
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
        selectors += cosmeticGeneric.filterNot { "$hostname|$it" in cosmeticExceptions }
        var h = hostname
        while (true) {
            cosmeticByDomain[h]?.let { list -> selectors += list.filterNot { "$hostname|$it" in cosmeticExceptions } }
            val dot = h.indexOf('.')
            if (dot == -1) break
            h = h.substring(dot + 1)
        }
        if (selectors.isEmpty()) return ""
        return selectors.joinToString(",\n") + " { display: none !important; }"
    }

    private fun hostnameOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()
}
