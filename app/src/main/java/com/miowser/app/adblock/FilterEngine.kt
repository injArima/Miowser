package com.miowser.app.adblock

sealed class FilterDecision {
    data object Allow : FilterDecision()
    data class Block(val rule: NetworkFilterRule) : FilterDecision()
}

/** Build once, then read concurrently. Manual tier wins; exceptions win within each tier. */
class FilterEngine : java.io.Serializable {
    private companion object { val literalToken = Regex("[a-zA-Z0-9_%.-]{5,}") }
    private class Index : java.io.Serializable {
        val domains = HashMap<String, MutableList<NetworkFilterRule>>()
        val generic = mutableListOf<NetworkFilterRule>()
        val tokens = FilterTokenIndex()
        fun add(rule: NetworkFilterRule) {
            val domain = rule.anchoredDomain
            if (domain != null) domains.getOrPut(domain) { mutableListOf() }.add(rule)
            else {
                // An exact literal is a necessary condition, never a substitute for full matching.
                val pattern = rule.raw.removePrefix("@@").substringBeforeLast('$', rule.raw.removePrefix("@@"))
                val token = literalToken.findAll(pattern)
                    .maxByOrNull { it.value.length }?.value?.take(5)?.lowercase(java.util.Locale.ROOT)
                if (token == null) {
                    check(generic.size < 2_048) { "Update exceeds capacity for unindexed filters; enabled selections unchanged" }
                    generic.add(rule)
                }
                else tokens.add(packFilterToken(token), rule)
            }
        }
        fun firstMatch(url: String, host: String, page: String, thirdParty: Boolean, type: ResourceType, urlTokens: Lazy<PackedUrlTokens?>): NetworkFilterRule? {
            var suffix = host
            while (true) {
                domains[suffix]?.firstOrNull { it.matches(url, host, thirdParty, type, page) }?.let { return it }
                val dot = suffix.indexOf('.')
                if (dot < 0) break
                suffix = suffix.substring(dot + 1)
            }
            if (tokens.isEmpty()) return generic.firstOrNull { it.matches(url, host, thirdParty, type, page) }
            val packed = urlTokens.value
            if (packed == null) {
                tokens.firstMatch { it.matches(url, host, thirdParty, type, page) }?.let { return it }
            } else packed.firstMatch { token -> tokens.get(token)?.firstOrNull {
                it.matches(url, host, thirdParty, type, page)
            } }?.let { return it }
            return generic.firstOrNull { it.matches(url, host, thirdParty, type, page) }
        }
    }
    private val manualAllow = Index()
    private val manualBlock = Index()
    private val subscribedAllow = Index()
    private val subscribedBlock = Index()
    fun addRule(rule: NetworkFilterRule) {
        when {
            rule.source == FilterSource.MANUAL && rule.isException -> manualAllow
            rule.source == FilterSource.MANUAL -> manualBlock
            rule.isException -> subscribedAllow
            else -> subscribedBlock
        }.add(rule)
    }
    fun classify(url: String, requestHostname: String, pageHostname: String, resourceType: ResourceType): FilterDecision {
        val host = canonicalHostname(requestHostname) ?: return FilterDecision.Allow
        val page = canonicalHostname(pageHostname) ?: return FilterDecision.Allow
        val thirdParty = !isSameRegistrableDomain(host, page)
        // Avoid unbounded temporary allocations; Unicode case folding needs the full matcher.
        val urlTokens = lazy(LazyThreadSafetyMode.NONE) {
            if (url.length <= 16_384 && url.all { it.code < 128 }) PackedUrlTokens(url) else null
        }
        if (manualAllow.firstMatch(url, host, page, thirdParty, resourceType, urlTokens) != null) return FilterDecision.Allow
        manualBlock.firstMatch(url, host, page, thirdParty, resourceType, urlTokens)?.let { return FilterDecision.Block(it) }
        if (subscribedAllow.firstMatch(url, host, page, thirdParty, resourceType, urlTokens) != null) return FilterDecision.Allow
        subscribedBlock.firstMatch(url, host, page, thirdParty, resourceType, urlTokens)?.let { return FilterDecision.Block(it) }
        return FilterDecision.Allow
    }
}
