package com.miowser.app.adblock

/**
 * Compiles Adblock Plus-syntax filter patterns — the subset EasyList actually uses: `||domain^`
 * anchors, `|` start/end anchors, `*` wildcards, `^` separator placeholders — into a [Regex].
 * True regex-literal rules (`/pattern/`) are a different, rarer EasyList syntax and are not
 * supported here; see [FilterListParser], which skips them rather than misinterpreting them.
 */
object AdblockPatternCompiler {
    private val domainCharacters = Regex("[a-zA-Z0-9.-]+")

    fun compile(pattern: String): Regex {
        return Regex(expression(pattern), RegexOption.IGNORE_CASE)
    }

    /** Keep the small expression; compiled Pattern nodes are created only for candidates. */
    internal fun expression(pattern: String): String {
        val sb = StringBuilder()
        var i = 0

        if (pattern.startsWith("||")) {
            // `||` anchors to the start of a (sub)domain: scheme://[anything.]rest
            sb.append("^[a-zA-Z][a-zA-Z0-9+.-]*://([a-zA-Z0-9-]+\\.)*")
            i = 2
        } else if (pattern.startsWith("|")) {
            sb.append("^")
            i = 1
        }

        val endAnchored = pattern.endsWith("|") && pattern.length > i
        val end = if (endAnchored) pattern.length - 1 else pattern.length

        while (i < end) {
            when (pattern[i]) {
                '*' -> { sb.append(".*"); i++ }
                '^' -> { sb.append("(?:[^a-zA-Z0-9_.%-]|$)"); i++ }
                else -> {
                    val start = i
                    while (i < end && pattern[i] != '*' && pattern[i] != '^') i++
                    sb.append(Regex.escape(pattern.substring(start, i)))
                }
            }
        }
        if (endAnchored) sb.append("$")

        return sb.toString()
    }

    /**
     * Best-effort domain extraction from a `||domain...` pattern, used only to build a fast
     * lookup index — returns null (meaning "can't cleanly index, check on every request")
     * whenever a wildcard touches the domain portion itself.
     */
    fun extractAnchorDomain(pattern: String): String? {
        if (!pattern.startsWith("||")) return null
        val rest = pattern.substring(2)
        val endIdx = rest.indexOfFirst { it == '/' || it == '^' || it == '*' || it == '|' || it == '$' }
        if (endIdx != -1 && rest[endIdx] == '*') return null
        val domainPart = if (endIdx == -1) rest else rest.substring(0, endIdx)
        if (domainPart.isBlank() || !domainPart.matches(domainCharacters)) return null
        return domainPart.lowercase()
    }
}
