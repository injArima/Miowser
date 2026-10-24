package com.miowser.app.adblock

/**
 * Allocation-free matching for a single quoted ASCII literal, with optional anchors and a
 * trailing separator. The descriptor references the existing expression; it owns no strings
 * or regex objects. Unrecognized expressions and Unicode comparisons use the regex matcher.
 */
internal object FilterStringPattern {
    const val FALLBACK = -1
    const val NO_MATCH = 0
    const val MATCH = 1

    private const val MASK = 0xfff
    private const val START = 1 shl 24
    private const val DOMAIN = 1 shl 25
    private const val END = 1 shl 26
    private const val SEPARATOR = 1 shl 27
    private const val DOMAIN_PREFIX = "^[a-zA-Z][a-zA-Z0-9+.-]*://([a-zA-Z0-9-]+\\.)*"
    private const val SEPARATOR_SUFFIX = "(?:[^a-zA-Z0-9_.%-]|$)"

    /** Recognize the compiler's exact shape, including expressions restored from older caches. */
    fun describe(expression: String?): Int {
        if (expression == null) return 0
        var flags = 0
        var offset = 0
        if (expression.startsWith(DOMAIN_PREFIX)) {
            flags = DOMAIN
            offset = DOMAIN_PREFIX.length
        } else if (expression.startsWith('^')) {
            flags = START
            offset = 1
        }
        if (!expression.startsWith("\\Q", offset)) return 0
        val start = offset + 2
        val end = expression.indexOf("\\E", start)
        val length = end - start
        if (length !in 1..MASK || start > MASK) return 0
        // Escaped embedded \\E sequences produce several quote blocks and are left to regex.
        for (index in start until end) {
            if (expression[index].code !in 32..126) return 0
        }
        offset = end + 2
        if (expression.startsWith(SEPARATOR_SUFFIX, offset)) {
            flags = flags or SEPARATOR
            offset += SEPARATOR_SUFFIX.length
        }
        if (offset < expression.length && expression[offset] == '$') {
            flags = flags or END
            offset++
        }
        if (offset != expression.length) return 0
        return flags or (start shl 12) or length
    }

    fun match(expression: String, descriptor: Int, url: String): Int {
        if (descriptor == 0) return FALLBACK
        // Regex $ also matches before a final line terminator. Keep its exact JVM/Android
        // behavior via fallback rather than silently treating it as String.endsWith.
        if (descriptor and END != 0 && url.isNotEmpty() && isLineTerminator(url[url.lastIndex])) return FALLBACK
        val start = (descriptor ushr 12) and MASK
        val length = descriptor and MASK
        if (descriptor and DOMAIN != 0) {
            var offset = 0
            while (offset < url.length) {
                val c = url[offset]
                if (c.code >= 128) return FALLBACK
                if (if (offset == 0) !isLetter(c) else !isLetter(c) && c !in '0'..'9' && c !in "+.-") break
                offset++
            }
            if (offset == 0 || !url.startsWith("://", offset)) return NO_MATCH
            offset += 3
            while (true) {
                val result = at(expression, start, length, descriptor, url, offset)
                if (result != NO_MATCH) return result
                val label = offset
                while (offset < url.length) {
                    val c = url[offset]
                    if (c.code >= 128) return FALLBACK
                    if (!isLetter(c) && c !in '0'..'9' && c != '-') break
                    offset++
                }
                if (offset == label || offset >= url.length || url[offset] != '.') return NO_MATCH
                offset++
            }
        }
        if (descriptor and START != 0) return at(expression, start, length, descriptor, url, 0)
        if (descriptor and END != 0) {
            val result = at(expression, start, length, descriptor, url, url.length - length)
            if (result != NO_MATCH || descriptor and SEPARATOR == 0) return result
            return at(expression, start, length, descriptor, url, url.length - length - 1)
        }
        for (offset in 0..url.length - length) {
            val result = at(expression, start, length, descriptor, url, offset)
            if (result != NO_MATCH) return result
        }
        return NO_MATCH
    }

    private fun at(expression: String, start: Int, length: Int, descriptor: Int, url: String, offset: Int): Int {
        if (offset < 0 || offset > url.length - length) return NO_MATCH
        for (index in 0 until length) {
            val actual = url[offset + index]
            // ASCII case folding is exact here. Unicode may have additional equivalences
            // (Kelvin sign, dotted I, long S, supplementary letters); defer the whole rule.
            if (actual.code >= 128) return FALLBACK
            val expected = expression[start + index]
            if (actual != expected && lower(actual) != lower(expected)) return NO_MATCH
        }
        val end = offset + length
        if (descriptor and SEPARATOR != 0 && end < url.length) {
            val c = url[end]
            if (c.code >= 128) return FALLBACK
            if (isLetter(c) || c in '0'..'9' || c in "_.%-") return NO_MATCH
            if (descriptor and END != 0 && end + 1 != url.length) return NO_MATCH
        } else if (descriptor and END != 0 && end != url.length) return NO_MATCH
        return MATCH
    }

    private fun lower(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c
    private fun isLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'
    private fun isLineTerminator(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'
}
