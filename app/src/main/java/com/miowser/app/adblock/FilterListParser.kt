package com.miowser.app.adblock

import java.io.BufferedReader

/**
 * Parses EasyList-format filter list text into [NetworkFilterRule]s and [CosmeticRule]s.
 * Unsupported constraints reject the entire rule, never silently widen its scope.
 * Native CSS selectors are supported; procedural filters and scriptlets are not.
 */
object FilterListParser {
    private val emptyOptions = FilterOptions()
    private val hostsWhitespace = Regex("\\s+")
    private val unsupportedSeparators = listOf("#?#", "#@?#", "#$#", "#@$#", "#%#", "#@%#")

    data class ScanResult(
        val networkRules: Int,
        val cosmeticRules: Int,
        val skippedRegex: Int,
        val skippedMalformed: Int,
        val skippedUnsupported: Int
    )

    data class ParseResult(
        val networkRules: List<NetworkFilterRule>,
        val cosmeticRules: List<CosmeticRule>,
        val skippedRegex: Int,
        val skippedMalformed: Int,
        val skippedUnsupported: Int = 0
    ) : java.io.Serializable

    fun parse(text: String, source: FilterSource, seen: MutableSet<String> = HashSet()): ParseResult {
        return parseLines(text.lineSequence(), source, seen)
    }

    /** Parse a downloaded file incrementally, without first materializing the complete list. */
    fun parse(reader: BufferedReader, source: FilterSource, seen: MutableSet<String> = HashSet()): ParseResult {
        return parseLines(boundedLines(reader), source, seen)
    }

    private fun parseLines(lines: Sequence<String>, source: FilterSource, seen: MutableSet<String>): ParseResult {
        val network = mutableListOf<NetworkFilterRule>()
        val cosmetic = mutableListOf<CosmeticRule>()
        // Keep the legacy caller-supplied keys for the small collecting API.
        val result = scanLines(lines, source, { seen.add("${source.name}:$it") }, network::add, cosmetic::add)
        return ParseResult(network, cosmetic, result.skippedRegex, result.skippedMalformed, result.skippedUnsupported)
    }

    /** Validation can count rules; engine builds can add them directly to their final indexes. */
    fun scan(reader: BufferedReader, source: FilterSource,
             acceptLine: (String) -> Boolean = { true },
             onNetwork: (NetworkFilterRule) -> Unit = {},
             onCosmetic: (CosmeticRule) -> Unit = {}): ScanResult =
        scanLines(boundedLines(reader), source, acceptLine, onNetwork, onCosmetic)

    private fun scanLines(lines: Sequence<String>, source: FilterSource,
                          acceptLine: (String) -> Boolean,
                          onNetwork: (NetworkFilterRule) -> Unit,
                          onCosmetic: (CosmeticRule) -> Unit): ScanResult {
        var network = 0
        var cosmetic = 0
        var skippedRegex = 0
        var skippedMalformed = 0
        var skippedUnsupported = 0
        val memory = FilterMemoryGuard()
        memory.check()
        var lineCount = 0

        for (rawLine in lines) {
            if (++lineCount % 256 == 0) memory.check()
            val line = rawLine.trim()
            if (line.length > FilterLimits.LINE_CHARS) { skippedMalformed++; continue }
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") ||
                (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#"))) continue

            // Peter Lowe's default list is a hosts file rather than ABP syntax.
            // Convert only valid hosts rows; never treat an arbitrary HTML page as a rule.
            if (line.startsWith("127.0.0.1 ") || line.startsWith("0.0.0.0 ") || line.startsWith("::1 ")) {
                val hostsParts = line.split(hostsWhitespace)
                for (host in hostsParts.drop(1).takeWhile { !it.startsWith('#') }) {
                    val domain = canonicalHostname(host)
                    if (domain == null || domain == "localhost") { skippedMalformed++; continue }
                    val rule = "||$domain^"
                    if (acceptLine(rule)) { onNetwork(parseNetworkRule(rule, source)); network++ }
                }
                continue
            }

            if (!acceptLine(line)) continue

            if (unsupportedSeparators.any(line::contains)) {
                skippedUnsupported++
                continue
            }
            val cosmeticSeparator = findCosmeticSeparator(line)
            if (cosmeticSeparator != null) {
                val parsed = parseCosmeticRule(line, cosmeticSeparator, source)
                if (parsed.isEmpty()) skippedUnsupported++ else parsed.forEach { onCosmetic(it); cosmetic++ }
                continue
            }

            val networkBody = line.removePrefix("@@")
            if (networkBody.startsWith("/") && (networkBody.endsWith("/") || networkBody.contains("/$"))) {
                skippedRegex++
                continue
            }

            try {
                val rule = parseNetworkRule(line, source)
                onNetwork(rule)
                network++
            }
            catch (_: UnsupportedFilterRule) { skippedUnsupported++ }
            catch (_: IllegalArgumentException) { skippedMalformed++ }
        }
        return ScanResult(network, cosmetic, skippedRegex, skippedMalformed, skippedUnsupported)
    }

    /** Oversized single lines are discarded before allocating a String proportional to the file. */
    private fun boundedLines(reader: BufferedReader): Sequence<String> = sequence {
        val buffer = CharArray(8 * 1024)
        val line = StringBuilder()
        var oversized = false
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            for (index in 0 until count) {
                val c = buffer[index]
                if (c == '\n' || c == '\r') {
                    if (line.isNotEmpty()) yield(line.toString())
                    line.setLength(0)
                    oversized = false
                } else if (!oversized) {
                    line.append(c)
                    if (line.length > FilterLimits.LINE_CHARS) oversized = true
                }
            }
        }
        if (line.isNotEmpty()) yield(line.toString())
    }

    fun parseNetworkRule(line: String, source: FilterSource): NetworkFilterRule {
        var body = line
        val isException = body.startsWith("@@")
        if (isException) body = body.substring(2)

        val dollarIdx = body.lastIndexOf('$')
        val pattern = if (dollarIdx >= 0) body.substring(0, dollarIdx) else body
        val optionsStr = if (dollarIdx >= 0) body.substring(dollarIdx + 1) else null
        require(pattern.isNotBlank() && pattern.none { it.isWhitespace() || it in "<>{}" }) { "Invalid filter pattern" }
        if (pattern.startsWith("/") && pattern.endsWith("/")) throw UnsupportedFilterRule()
        if (pattern.length > 2_048 || pattern.count { it == '*' } > 16) throw UnsupportedFilterRule()
        val options = optionsStr?.let(::parseOptions) ?: emptyOptions
        // Document exceptions in full ABP engines can whitelist an entire page; we do not
        // implement that scope yet. Never reinterpret one as a plain network exception.
        if (isException && ResourceType.DOCUMENT in options.includedResourceTypes) throw UnsupportedFilterRule()

        val anchorDomain = AdblockPatternCompiler.extractAnchorDomain(pattern)
        val isPlainDomainAnchor = anchorDomain != null && pattern == "||$anchorDomain^"

        return NetworkFilterRule(
            raw = line,
            isException = isException,
            anchoredDomain = anchorDomain,
            regexPattern = if (isPlainDomainAnchor) null else AdblockPatternCompiler.expression(pattern),
            options = options,
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
        "websocket" to ResourceType.WEBSOCKET,
        "xhr" to ResourceType.XHR,
        "other" to ResourceType.OTHER
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
                t == "third-party" || t == "3p" || t == "~first-party" || t == "~1p" -> {
                    require(thirdPartyOnly != false) { "Conflicting party constraints" }
                    thirdPartyOnly = true
                }
                t == "~third-party" || t == "first-party" || t == "1p" || t == "~3p" -> {
                    require(thirdPartyOnly != true) { "Conflicting party constraints" }
                    thirdPartyOnly = false
                }
                resourceType != null -> {
                    if (negated) excludedTypes += resourceType else includedTypes += resourceType
                }
                t.startsWith("domain=") -> {
                    for (d in t.removePrefix("domain=").split('|')) {
                        val domain = requireNotNull(canonicalHostname(d.removePrefix("~"))) { "Invalid domain constraint" }
                        if (d.startsWith("~")) excludedDomains += domain else includedDomains += domain
                    }
                }
                else -> throw UnsupportedFilterRule()
            }
        }
        return FilterOptions(includedTypes.ifEmpty { emptySet() }, excludedTypes.ifEmpty { emptySet() },
            thirdPartyOnly, includedDomains.ifEmpty { emptySet() }, excludedDomains.ifEmpty { emptySet() })
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
        if (selector.startsWith("+js(") || selector.startsWith("script:inject(") ||
            selector.contains(":xpath(") || selector.contains(":matches-css") ||
            selector.contains(":style(") || selector.contains(":remove(") ||
            selector.contains(":has-text(") || selector.contains(":contains(") ||
            selector.contains(":upward(") || selector.contains(":matches-path(") ||
            selector.contains(":matches-attr(") || selector.contains(":matches-prop(") ||
            selector.contains(":watch-attr(") || selector.contains(":others(") ||
            selector.contains(":not(:has-text(") || selector.any { it == '{' || it == '}' }
        ) {
            return emptyList() // extended/procedural cosmetic syntax not supported
        }

        val isException = line.contains("#@#")
        if (domainsPart.isBlank()) {
            return listOf(CosmeticRule(domain = null, selector = selector, isException = isException, source = source))
        }

        val tokens = domainsPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val normalized = tokens.map { token ->
            val domain = canonicalHostname(token.removePrefix("~")) ?: return emptyList()
            domain to token.startsWith("~")
        }
        val includedDomains = normalized.filterNot { it.second }.map { it.first }
        val excludedDomains = normalized.filter { it.second }.map { it.first }

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

private class UnsupportedFilterRule : IllegalArgumentException("Unsupported filter constraint")
