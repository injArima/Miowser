package com.miowser.app.adblock

sealed class FilterDecision {
    data object Allow : FilterDecision()
    data class Block(val rule: NetworkFilterRule) : FilterDecision()
}

/**
 * Indexed network-rule matcher. Domain-anchored rules (`||domain^...`, the overwhelming
 * majority in real lists like EasyList) are indexed by their anchor domain for fast candidate
 * lookup; everything else falls into a smaller generic bucket checked on every request. Manual
 * (user) rules are checked before subscribed-list rules, and any exception match short-circuits
 * to Allow immediately — the priority order validated against 1DM's own three-tier engine
 * design (see project memory: 1dm-adblock-engine.md), simplified to two tiers since Miowser
 * doesn't have a separate popup-specific engine yet.
 */
class FilterEngine {
    private val domainIndexed = HashMap<String, MutableList<NetworkFilterRule>>()
    private val generic = mutableListOf<NetworkFilterRule>()

    fun addRule(rule: NetworkFilterRule) {
        val anchor = rule.anchoredDomain
        if (anchor != null) {
            domainIndexed.getOrPut(anchor) { mutableListOf() }.add(rule)
        } else {
            generic.add(rule)
        }
    }

    fun classify(
        url: String,
        requestHostname: String,
        pageHostname: String,
        resourceType: ResourceType
    ): FilterDecision {
        val isThirdParty = !isSameRegistrableDomain(requestHostname, pageHostname)
        val candidates = candidatesFor(requestHostname)
            .sortedBy { if (it.source == FilterSource.MANUAL) 0 else 1 }

        var blockedBy: NetworkFilterRule? = null
        for (rule in candidates) {
            if (!rule.matches(url, requestHostname, isThirdParty, resourceType, pageHostname)) continue
            if (rule.isException) return FilterDecision.Allow
            if (blockedBy == null) blockedBy = rule
        }
        return blockedBy?.let { FilterDecision.Block(it) } ?: FilterDecision.Allow
    }

    private fun candidatesFor(hostname: String): List<NetworkFilterRule> {
        val result = mutableListOf<NetworkFilterRule>()
        result.addAll(generic)
        var h = hostname
        while (true) {
            domainIndexed[h]?.let { result.addAll(it) }
            val dot = h.indexOf('.')
            if (dot == -1) break
            h = h.substring(dot + 1)
        }
        return result
    }
}
