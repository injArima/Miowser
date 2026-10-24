package com.miowser.app.adblock

import java.io.DataInputStream
import java.io.DataOutputStream

sealed class FilterDecision {
    data object Allow : FilterDecision()
    data class Block(val rule: NetworkFilterRule) : FilterDecision()
}

/** Build once, then read concurrently. Manual tier wins; exceptions win within each tier. */
class FilterEngine : java.io.Serializable {
    private companion object { val literalToken = Regex("[a-zA-Z0-9_%.-]{3,}") }
    private class Index : java.io.Serializable {
        var domains = HashMap<String, MutableList<NetworkFilterRule>>()
        var generic: MutableList<NetworkFilterRule> = mutableListOf()
        var tokens = FilterTokenIndex()
        var shortTokens = FilterTokenIndex()
        fun hasTokenRules(): Boolean = !tokens.isEmpty() || !shortTokens.isEmpty()
        fun forEachTokenRule(action: (NetworkFilterRule) -> Unit) {
            tokens.forEachRule(action)
            shortTokens.forEachRule(action)
        }

        fun optimizeTokens(frequencies: FilterTokenFrequencies, memory: FilterMemoryGuard) {
            if (!hasTokenRules()) return
            val nextTokens = FilterTokenIndex()
            val nextShortTokens = FilterTokenIndex()
            var visited = 0
            forEachTokenRule { rule ->
                if (visited++ % 256 == 0) memory.check()
                val width = FilterTokenSelection.width(rule.raw)
                check(width != 0) { "Indexed rule has no required literal" }
                val key = frequencies.select(rule.raw, width)
                if (width == 5) nextTokens.add(key, rule, memory) else nextShortTokens.add(key, rule, memory)
            }
            // Until both new tables are complete, this tier retains its original valid buckets.
            tokens = nextTokens
            shortTokens = nextShortTokens
        }
        fun forEachRule(action: (NetworkFilterRule) -> Unit) {
            domains.values.forEach { bucket -> bucket.forEach(action) }
            generic.forEach(action)
            tokens.forEachRule(action)
            shortTokens.forEachRule(action)
        }
        fun add(rule: NetworkFilterRule) {
            val domain = rule.anchoredDomain
            if (domain != null) domains.getOrPut(domain) { mutableListOf() }.add(rule)
            else {
                // An exact literal is a necessary condition, never a substitute for full matching.
                val pattern = rule.raw.removePrefix("@@").substringBeforeLast('$', rule.raw.removePrefix("@@"))
                val token = literalToken.findAll(pattern)
                    .maxByOrNull { it.value.length }?.value?.take(5)?.lowercase(java.util.Locale.ROOT)
                if (token == null) {
                    generic.add(rule)
                } else if (token.length == 5) tokens.add(packFilterToken(token), rule)
                else shortTokens.add(packFilterToken(token.take(3)), rule)
            }
        }

        fun writeSnapshot(output: DataOutputStream, writeRule: (NetworkFilterRule) -> Unit) {
            output.writeInt(domains.size)
            domains.forEach { (domain, rules) ->
                output.writeUTF(domain)
                output.writeInt(rules.size)
                rules.forEach(writeRule)
            }
            output.writeInt(generic.size)
            generic.forEach(writeRule)
            tokens.writeSnapshot(output, writeRule)
            shortTokens.writeSnapshot(output, writeRule)
        }

        fun readSnapshot(input: DataInputStream, ruleCount: Int, memory: FilterMemoryGuard,
                         readRule: () -> NetworkFilterRule) {
            val domainCount = input.readInt()
            require(domainCount >= 0 && domainCount <= ruleCount) { "Invalid domain bucket count" }
            memory.check(domainCount.toLong() * 64 + 64)
            val initialCapacity = (domainCount.toLong() * 4 / 3 + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            domains = HashMap(initialCapacity)
            repeat(domainCount) {
                memory.check()
                val domain = input.readUTF()
                check(domain.isNotEmpty() && !domains.containsKey(domain)) { "Duplicate or empty domain bucket" }
                val bucket = readBucket(input, ruleCount, memory) {
                    readRule().also { check(it.anchoredDomain == domain) { "Rule in wrong domain bucket" } }
                }
                check(bucket.isNotEmpty()) { "Empty domain bucket" }
                domains[domain] = bucket
            }
            generic = readBucket(input, ruleCount, memory) {
                readRule().also { check(it.anchoredDomain == null) { "Domain rule in generic bucket" } }
            }
            tokens.readSnapshot(input, ruleCount, 5, memory, readRule)
            shortTokens.readSnapshot(input, ruleCount, 3, memory, readRule)
        }

        private fun readBucket(input: DataInputStream, ruleCount: Int, memory: FilterMemoryGuard,
                               readRule: () -> NetworkFilterRule): MutableList<NetworkFilterRule> {
            val count = input.readInt()
            require(count >= 0 && count <= ruleCount) { "Invalid bucket rule count" }
            memory.check(count.toLong() * 8 + 32)
            return ArrayList<NetworkFilterRule>(count).apply { repeat(count) { add(readRule()) } }
        }
        fun firstMatch(url: String, host: String, page: String, thirdParty: Boolean, type: ResourceType,
            urlTokens: Lazy<PackedUrlTokens?>, shortUrlTokens: Lazy<PackedUrlTokens?>, checkDomains: Boolean): NetworkFilterRule? {
            if (checkDomains) {
                var suffix = host
                while (true) {
                    domains[suffix]?.firstOrNull { it.matches(url, host, thirdParty, type, page) }?.let { return it }
                    val dot = suffix.indexOf('.')
                    if (dot < 0) break
                    suffix = suffix.substring(dot + 1)
                }
            }
            if (!tokens.isEmpty()) {
                val packed = urlTokens.value
                if (packed == null) {
                    tokens.firstMatch { it.matches(url, host, thirdParty, type, page) }?.let { return it }
                } else packed.firstMatch { token -> tokens.get(token)?.firstOrNull {
                    it.matches(url, host, thirdParty, type, page)
                } }?.let { return it }
            }
            if (!shortTokens.isEmpty()) {
                val packed = shortUrlTokens.value
                if (packed == null) {
                    shortTokens.firstMatch { it.matches(url, host, thirdParty, type, page) }?.let { return it }
                } else packed.firstMatch { token -> shortTokens.get(token)?.firstOrNull {
                    it.matches(url, host, thirdParty, type, page)
                } }?.let { return it }
            }
            return generic.firstOrNull { it.matches(url, host, thirdParty, type, page) }
        }

        fun matchingOrigins(url: String, host: String, page: String, thirdParty: Boolean, type: ResourceType): Set<Int> {
            val result = HashSet<Int>()
            fun consider(rule: NetworkFilterRule) {
                if (rule.matches(url, host, thirdParty, type, page)) result.add(rule.originGroup)
            }
            var suffix = host
            while (true) {
                domains[suffix]?.forEach(::consider)
                val dot = suffix.indexOf('.')
                if (dot < 0) break
                suffix = suffix.substring(dot + 1)
            }
            if (url.length <= 16_384 && url.all { it.code < 128 }) {
                if (!tokens.isEmpty()) PackedUrlTokens(url).firstMatch { key -> tokens.get(key)?.forEach(::consider); null }
                if (!shortTokens.isEmpty()) PackedUrlTokens(url, 3).firstMatch { key -> shortTokens.get(key)?.forEach(::consider); null }
            } else {
                tokens.forEachRule(::consider)
                shortTokens.forEachRule(::consider)
            }
            generic.forEach(::consider)
            return result
        }
    }
    private val manualAllow = Index()
    private val manualBlock = Index()
    private val subscribedAllow = Index()
    private val subscribedBlock = Index()
    internal fun forEachRule(action: (NetworkFilterRule) -> Unit) {
        manualAllow.forEachRule(action)
        manualBlock.forEachRule(action)
        subscribedAllow.forEachRule(action)
        subscribedBlock.forEachRule(action)
    }

    /** Builder-only pass. No source reparse, full rule copy, or histogram retained after publish. */
    internal fun optimizeTokenIndexes(memory: FilterMemoryGuard = FilterMemoryGuard()): Boolean {
        val indexes = listOf(manualAllow, manualBlock, subscribedAllow, subscribedBlock)
        if (indexes.none { it.hasTokenRules() }) return true
        return try {
            memory.check(FilterTokenFrequencies.BYTES)
            val frequencies = FilterTokenFrequencies()
            var visited = 0
            indexes.forEach { index ->
                index.forEachTokenRule { rule ->
                    if (visited++ % 256 == 0) memory.check()
                    frequencies.record(rule.raw)
                }
            }
            indexes.forEach { it.optimizeTokens(frequencies, memory) }
            true
        } catch (_: FilterMemoryPressureException) {
            // Bucket selection is optional. Every remaining tier can use its previous index.
            false
        }
    }

    /** Diagnostic count before full predicates, excluding separately indexed domain rules. */
    internal fun urlRuleCandidateCount(url: String): Int {
        val canPack = url.length <= 16_384 && url.all { it.code < 128 }
        val packed = if (canPack) PackedUrlTokens(url) else null
        val shortPacked = if (canPack) PackedUrlTokens(url, 3) else null
        var count = 0
        listOf(manualAllow, manualBlock, subscribedAllow, subscribedBlock).forEach { index ->
            count += index.generic.size
            if (packed == null) index.tokens.forEachRule { count++ }
            else packed.firstMatch { key -> count += index.tokens.get(key)?.size ?: 0; null }
            if (shortPacked == null) index.shortTokens.forEachRule { count++ }
            else shortPacked.firstMatch { key -> count += index.shortTokens.get(key)?.size ?: 0; null }
        }
        return count
    }

    /** Save completed buckets in matching order, without another rule-ID map or copied rule list. */
    internal fun writeIndexedSnapshot(output: DataOutputStream, writeRule: (NetworkFilterRule) -> Unit) {
        manualAllow.writeSnapshot(output, writeRule)
        manualBlock.writeSnapshot(output, writeRule)
        subscribedAllow.writeSnapshot(output, writeRule)
        subscribedBlock.writeSnapshot(output, writeRule)
    }

    /** A fresh engine restores bucket membership directly; addRule and tokenization are bypassed. */
    internal fun readIndexedSnapshot(input: DataInputStream, ruleCount: Int,
                                     readRule: () -> NetworkFilterRule) {
        require(ruleCount >= 0) { "Invalid network rule count" }
        var remaining = ruleCount
        val memory = FilterMemoryGuard()
        fun restore(index: Index, source: FilterSource, exception: Boolean) {
            index.readSnapshot(input, ruleCount, memory) {
                check(remaining > 0) { "Too many indexed rules" }
                if (remaining % 256 == 0) memory.check()
                remaining--
                readRule().also {
                    check(it.source == source && it.isException == exception) { "Rule in wrong priority tier" }
                }
            }
        }
        restore(manualAllow, FilterSource.MANUAL, true)
        restore(manualBlock, FilterSource.MANUAL, false)
        restore(subscribedAllow, FilterSource.SUBSCRIBED, true)
        restore(subscribedBlock, FilterSource.SUBSCRIBED, false)
        check(remaining == 0) { "Missing indexed rules" }
    }
    /** Native index is a conservative prefilter; every candidate still uses the full Kotlin matcher. */
    internal fun anchoredDomainMasks(): Map<String, Int> = HashMap<String, Int>().also { masks ->
        val memory = FilterMemoryGuard()
        val tiers = listOf(manualAllow to 1, manualBlock to 2, subscribedAllow to 4, subscribedBlock to 8)
        // Include entry nodes and temporary hash-table growth, not just the final references.
        memory.check(tiers.sumOf { it.first.domains.size.toLong() } * 80 + 64)
        var visited = 0
        tiers.forEach { (index, bit) ->
            index.domains.keys.forEach { domain ->
                if (visited++ % 256 == 0) memory.check()
                masks[domain] = (masks[domain] ?: 0) or bit
            }
        }
    }
    fun addRule(rule: NetworkFilterRule) {
        when {
            rule.source == FilterSource.MANUAL && rule.isException -> manualAllow
            rule.source == FilterSource.MANUAL -> manualBlock
            rule.isException -> subscribedAllow
            else -> subscribedBlock
        }.add(rule)
    }

    /** Only called to explain an already-blocked navigation; resource interception stays unchanged. */
    internal fun blockingOriginGroups(url: String, host: String, page: String, type: ResourceType,
                                     decision: FilterDecision.Block): Set<Int> {
        val tier = if (decision.rule.source == FilterSource.MANUAL) manualBlock else subscribedBlock
        return tier.matchingOrigins(url, host, page, !isSameRegistrableDomain(host, page), type)
    }
    fun classify(url: String, requestHostname: String, pageHostname: String, resourceType: ResourceType,
        domainMask: Int? = null): FilterDecision {
        val host = canonicalHostname(requestHostname) ?: return FilterDecision.Allow
        val page = canonicalHostname(pageHostname) ?: return FilterDecision.Allow
        val thirdParty = !isSameRegistrableDomain(host, page)
        // Avoid unbounded temporary allocations; Unicode case folding needs the full matcher.
        val urlTokens = lazy(LazyThreadSafetyMode.NONE) {
            if (url.length <= 16_384 && url.all { it.code < 128 }) PackedUrlTokens(url) else null
        }
        val shortUrlTokens = lazy(LazyThreadSafetyMode.NONE) {
            if (url.length <= 16_384 && url.all { it.code < 128 }) PackedUrlTokens(url, 3) else null
        }
        if (manualAllow.firstMatch(url, host, page, thirdParty, resourceType, urlTokens, shortUrlTokens, domainMask == null || domainMask and 1 != 0) != null) return FilterDecision.Allow
        manualBlock.firstMatch(url, host, page, thirdParty, resourceType, urlTokens, shortUrlTokens, domainMask == null || domainMask and 2 != 0)?.let { return FilterDecision.Block(it) }
        if (subscribedAllow.firstMatch(url, host, page, thirdParty, resourceType, urlTokens, shortUrlTokens, domainMask == null || domainMask and 4 != 0) != null) return FilterDecision.Allow
        subscribedBlock.firstMatch(url, host, page, thirdParty, resourceType, urlTokens, shortUrlTokens, domainMask == null || domainMask and 8 != 0)?.let { return FilterDecision.Block(it) }
        return FilterDecision.Allow
    }
}
