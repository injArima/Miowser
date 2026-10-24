package com.miowser.app.adblock

/** Only literal ASCII windows are indexed. Wildcards/separators never become required tokens. */
internal object FilterTokenSelection {
    private fun isLiteral(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '%' || c == '.' || c == '-'

    /** Prefer five-byte windows; a rule with only shorter literals keeps the three-byte index. */
    fun width(raw: String): Int {
        val end = raw.lastIndexOf('$').takeIf { it >= 0 } ?: raw.length
        var run = 0
        var longest = 0
        for (index in 0 until end) {
            run = if (isLiteral(raw[index])) run + 1 else 0
            if (run >= 5) return 5
            longest = maxOf(longest, run)
        }
        return if (longest >= 3) 3 else 0
    }

    /** A rolling exact key avoids allocating a substring or candidate collection per window. */
    inline fun forEach(raw: String, width: Int, action: (Long) -> Unit) {
        require(width == 3 || width == 5)
        val end = raw.lastIndexOf('$').takeIf { it >= 0 } ?: raw.length
        val mask = (1L shl (width * 8)) - 1
        var key = 0L
        var run = 0
        for (index in 0 until end) {
            val c = raw[index]
            if (!isLiteral(c)) {
                key = 0L
                run = 0
            } else {
                val folded = if (c in 'A'..'Z') c.code + 32 else c.code
                key = ((key shl 8) or folded.toLong()) and mask
                if (++run >= width) action(key)
            }
        }
    }
}

/** Bounded approximate counts: collisions only change token choice, never matching decisions. */
internal class FilterTokenFrequencies {
    private val counts = IntArray(BUCKETS)

    fun record(raw: String) {
        val width = FilterTokenSelection.width(raw)
        if (width == 0) return
        FilterTokenSelection.forEach(raw, width) { key ->
            val slot = slot(key, width)
            if (counts[slot] < Int.MAX_VALUE) counts[slot]++
        }
    }

    fun select(raw: String, width: Int): Long {
        var best = -1L
        var frequency = Int.MAX_VALUE
        FilterTokenSelection.forEach(raw, width) { key ->
            val count = counts[slot(key, width)]
            if (best < 0 || count < frequency) {
                best = key
                frequency = count
            }
        }
        check(best >= 0) { "Tokenized rule has no required literal" }
        return best
    }

    private fun slot(key: Long, width: Int): Int = tokenSlot(key xor (width.toLong() shl 48), BUCKETS - 1)

    companion object {
        private const val BUCKETS = 65_536
        const val BYTES = BUCKETS * 4L
    }
}
