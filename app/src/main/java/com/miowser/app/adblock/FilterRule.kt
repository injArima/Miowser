package com.miowser.app.adblock

/** Where a rule came from — user-authored (manual) rules win over subscribed-list rules. */
enum class FilterSource { MANUAL, SUBSCRIBED }

data class FilterOptions(
    val includedResourceTypes: Set<ResourceType> = emptySet(), // $script,image,... — empty = no inclusion restriction
    val excludedResourceTypes: Set<ResourceType> = emptySet(), // $~script,~image,... — always excluded, even if included above
    val thirdPartyOnly: Boolean? = null, // true=$third-party, false=$~third-party/$first-party, null=either
    val includedDomains: Set<String> = emptySet(), // $domain=a.com|b.com
    val excludedDomains: Set<String> = emptySet() // $domain=~a.com
) : java.io.Serializable

/**
 * A parsed EasyList-style network filter rule. [anchoredDomain] (when present) is a best-effort
 * domain extraction used to index the rule for fast lookup (see [FilterEngine]).
 *
 * [regex] is null for the common case of a *plain* domain rule — a pattern that is exactly
 * `||domain^` with nothing else (82% of real EasyList network rules, measured against the
 * bundled list). Those need no pattern matching at all beyond "is this the anchor domain or a
 * subdomain of it", so compiling a java.util.regex.Pattern for each one — the dominant cost in
 * the ~15-27s cold-start parse — would be pure waste. Only rules with actual path/wildcard
 * content beyond the domain anchor store an expression. Simple literal/anchor expressions use
 * direct string comparisons; complex patterns use a bounded cache instead of retaining a
 * Pattern object graph for every subscribed rule.
 */
data class NetworkFilterRule(
    val raw: String,
    val isException: Boolean,
    val anchoredDomain: String?,
    val regexPattern: String?,
    val options: FilterOptions,
    val source: FilterSource
) : java.io.Serializable {
    /** Builder-owned provenance; excluded from equality and unchanged after publication. */
    internal var originGroup: Int = 0
    /** One packed integer, pointing into regexPattern; no copied literal or per-rule matcher. */
    internal val stringPattern: Int = FilterStringPattern.describe(regexPattern)
    val regex: Regex? get() = regexPattern?.let(FilterPatternCache::get)
    fun matches(
        url: String,
        requestHostname: String,
        isThirdParty: Boolean,
        resourceType: ResourceType,
        pageHostname: String
    ): Boolean {
        if (resourceType in options.excludedResourceTypes) return false
        if (options.includedResourceTypes.isNotEmpty() && resourceType !in options.includedResourceTypes) return false
        options.thirdPartyOnly?.let { wantThirdParty ->
            if (wantThirdParty != isThirdParty) return false
        }
        if (options.includedDomains.isNotEmpty() && options.includedDomains.none { matchesDomain(pageHostname, it) }) {
            return false
        }
        if (options.excludedDomains.isNotEmpty() && options.excludedDomains.any { matchesDomain(pageHostname, it) }) {
            return false
        }
        val expression = regexPattern
        return if (expression != null) {
            when (FilterStringPattern.match(expression, stringPattern, url)) {
                FilterStringPattern.MATCH -> true
                FilterStringPattern.NO_MATCH -> false
                else -> FilterPatternCache.get(expression).containsMatchIn(url)
            }
        } else {
            matchesDomain(requestHostname, requireNotNull(anchoredDomain) {
                "plain-domain rule (null regex) must have a non-null anchoredDomain: $raw"
            })
        }
    }

    private fun matchesDomain(hostname: String, ruleDomain: String): Boolean = hostnameMatchesDomain(hostname, ruleDomain)
}

/** Shared across installed and rebuilding generations; retained compiled patterns stay bounded. */
internal object FilterPatternCache {
    private val patterns = BoundedFilterCache<String, Regex>(256, 2 * 1024 * 1024) { expression, _ ->
        // Conservative allowance for regex nodes as well as the expression text.
        (expression.length * 128L + 512).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun get(expression: String): Regex = patterns.get(expression)
        ?: Regex(expression, RegexOption.IGNORE_CASE).also { patterns.put(expression, it) }
}

/**
 * A parsed cosmetic (element-hiding) rule: `domain##selector`, `##selector`, or `domain#@#selector`
 * — plus EasyList's domain-exclusion form, `domain1,~domain2##selector` ("apply on domain1 and
 * its subdomains, except domain2 and its subdomains"). [excludedDomains] is empty for the common
 * case; when present, [AdBlockEngine.cosmeticCssFor] checks it against the *actual page hostname*
 * for every emission of this rule, regardless of which domain-hierarchy level (domain, or one of
 * its own ancestors) is what matched [domain] in the first place.
 */
data class CosmeticRule(
    val domain: String?, // null = applies everywhere (subject to excludedDomains below)
    val selector: String,
    val isException: Boolean,
    val source: FilterSource,
    val excludedDomains: List<String> = emptyList()
) : java.io.Serializable
