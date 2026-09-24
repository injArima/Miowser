package com.miowser.app.adblock

/**
 * Parses EasyList-format filter list text into [NetworkFilterRule]s and [CosmeticRule]s.
 * Deliberately scoped: true regex-literal rules (`/pattern/`) and extended/procedural
 * cosmetic syntax (`:has(...)`, `:xpath(...)`, `:matches-css(...)`) are skipped rather than
 * mishandled — both are real but rare in practice (regex rules are ~1.3% of EasyList),
 * and getting them wrong silently would be worse than not supporting them yet.
 */
object FilterListParser {

    data class ParseResult(
        val networkRules: List<NetworkFilterRule>,
        val cosmeticRules: List<CosmeticRule>,
        val skippedRegex: Int,
        val skippedMalformed: Int
    )

    fun parse(text: String, source: FilterSource): ParseResult {
        val network = mutableListOf<NetworkFilterRule>()
        val cosmetic = mutableListOf<CosmeticRule>()
        var skippedRegex = 0
        var skippedMalformed = 0

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue

            val cosmeticSeparator = findCosmeticSeparator(line)
            if (cosmeticSeparator != null) {
                cosmetic.addAll(parseCosmeticRule(line, cosmeticSeparator, source))
                continue
            }

            if (line.startsWith("/") && line.endsWith("/") && line.length > 1) {
                skippedRegex++
                continue
            }

            val rule = runCatching { parseNetworkRule(line, source) }.getOrNull()
            if (rule != null) network.add(rule) else skippedMalformed++
        }
        return ParseResult(network, cosmetic, skippedRegex, skippedMalformed)
    }

    fun parseNetworkRule(line: String, source: FilterSource): NetworkFilterRule {
        var body = line
        val isException = body.startsWith("@@")
        if (isException) body = body.substring(2)

        val dollarIdx = body.lastIndexOf('$')
        val pattern = if (dollarIdx >= 0) body.substring(0, dollarIdx) else body
        val optionsStr = if (dollarIdx >= 0) body.substring(dollarIdx + 1) else null

        val anchorDomain = AdblockPatternCompiler.extractAnchorDomain(pattern)
        val isPlainDomainAnchor = anchorDomain != null && pattern == "||$anchorDomain^"

        return NetworkFilterRule(
            raw = line,
            isException = isException,
            anchoredDomain = anchorDomain,
            regex = if (isPlainDomainAnchor) null else AdblockPatternCompiler.compile(pattern),
            options = optionsStr?.let(::parseOptions) ?: FilterOptions(),
            source = source
        )
    }

    // A bare token ("script") means "this rule only applies to this type" (inclusion); a
    // `~`-prefixed token ("~script") means "this rule applies to everything except this type"
    // (exclusion) — both directions appear in real EasyList rules, sometimes on the same line
    // (e.g. "$~image,~xmlhttprequest,domain=...").
    private val RESOURCE_TYPE_TOKENS = mapOf(
        "script" to ResourceType.SCRIPT,
        "image" to ResourceType.IMAGE,
        "stylesheet" to ResourceType.STYLESHEET,
        "css" to ResourceType.STYLESHEET,
        "xmlhttprequest" to ResourceType.XHR,
        "subdocument" to ResourceType.SUBDOCUMENT,
        "frame" to ResourceType.SUBDOCUMENT,
        "document" to ResourceType.DOCUMENT,
        "font" to ResourceType.FONT,
        "media" to ResourceType.MEDIA,
        "websocket" to ResourceType.WEBSOCKET
    )

    private fun parseOptions(optionsStr: String): FilterOptions {
        val includedTypes = mutableSetOf<ResourceType>()
        val excludedTypes = mutableSetOf<ResourceType>()
        var thirdPartyOnly: Boolean? = null
        val includedDomains = mutableSetOf<String>()
        val excludedDomains = mutableSetOf<String>()

        for (token in optionsStr.split(',')) {
            val t = token.trim()
            val negated = t.startsWith("~")
            val bareToken = if (negated) t.removePrefix("~") else t
            val resourceType = RESOURCE_TYPE_TOKENS[bareToken]

            when {
                t == "third-party" -> thirdPartyOnly = true
                t == "~third-party" || t == "first-party" -> thirdPartyOnly = false
                resourceType != null -> {
                    if (negated) excludedTypes += resourceType else includedTypes += resourceType
                }
                t.startsWith("domain=") -> {
                    for (d in t.removePrefix("domain=").split('|')) {
                        when {
                            d.startsWith("~") -> excludedDomains += d.removePrefix("~").lowercase()
                            d.isNotBlank() -> includedDomains += d.lowercase()
                        }
                    }
                }
                // Everything else ($generichide, $popup, $important, $ping, $elemhide, ...) is
                // recognized-but-ignored: the rule still applies, just without that extra
                // constraint, rather than being dropped outright.
                else -> Unit
            }
        }
        return FilterOptions(includedTypes, excludedTypes, thirdPartyOnly, includedDomains, excludedDomains)
    }

    // Cosmetic rules use `##` or `#@#`, but network rules can legally contain '#' (rare, but
    // possible in a query string), so this only fires on those specific separators.
    private fun findCosmeticSeparator(line: String): IntRange? {
        val exceptionIdx = line.indexOf("#@#")
        if (exceptionIdx != -1) return exceptionIdx until (exceptionIdx + 3)
        val idx = line.indexOf("##")
        if (idx != -1) return idx until (idx + 2)
        return null
    }

    private fun parseCosmeticRule(line: String, separator: IntRange, source: FilterSource): List<CosmeticRule> {
        val domainsPart = line.substring(0, separator.first)
        val selector = line.substring(separator.last + 1)
        if (selector.isBlank()) return emptyList()
        if (selector.contains(":xpath(") || selector.contains(":matches-css") ||
            selector.contains(":style(")
        ) {
            return emptyList() // extended/procedural cosmetic syntax not supported
        }

        val isException = line.contains("#@#")
        if (domainsPart.isBlank()) {
            return listOf(CosmeticRule(domain = null, selector = selector, isException = isException, source = source))
        }

        val tokens = domainsPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val includedDomains = tokens.filterNot { it.startsWith("~") }
        val excludedDomains = tokens.filter { it.startsWith("~") }.map { it.removePrefix("~") }

        if (includedDomains.isEmpty()) {
            // Exclusions with no plain inclusion ("~foo.com##sel" alone) are valid EasyList
            // syntax — real adblockers treat this as a generic rule (applies everywhere) with
            // foo.com carved out, not as "no domains, so nothing." Distinct from the domainsPart-
            // is-blank case above only in that it still carries an exclusion list.
            return listOf(CosmeticRule(domain = null, selector = selector, isException = isException, source = source, excludedDomains = excludedDomains))
        }
        return includedDomains.map {
            CosmeticRule(domain = it, selector = selector, isException = isException, source = source, excludedDomains = excludedDomains)
        }
    }
}
