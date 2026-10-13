package com.miowser.app.adblock

/** Five ASCII characters packed exactly into 40 bits, not a lossy token hash.
 * Open-addressed primitive tables avoid one substring/object per URL window. */
internal fun packFilterToken(value: String): Long {
    var result = 0L
    for (c in value) result = (result shl 8) or c.code.toLong()
    return result
}
internal fun tokenSlot(key: Long, mask: Int): Int {
    val mixed = (key xor (key ushr 21) xor (key ushr 33)) * -7046029254386353131L
    return (mixed ushr 32).toInt() and mask
}
internal class PackedUrlTokens(url: String) {
    private val table: LongArray
    init {
        var capacity = 16
        while (capacity < (url.length - 4).coerceAtLeast(0) * 2) capacity *= 2
        table = LongArray(capacity)
        var token = 0L
        url.forEachIndexed { index, c ->
            val folded = if (c in 'A'..'Z') c.code + 32 else c.code
            token = ((token shl 8) or folded.toLong()) and 0xFFFFFFFFFFL
            if (index >= 4) {
                val stored = token + 1 // 0 is an empty slot, including for five NUL characters.
                var slot = tokenSlot(token, capacity - 1)
                while (table[slot] != 0L && table[slot] != stored) slot = (slot + 1) and (capacity - 1)
                table[slot] = stored
            }
        }
    }
    fun firstMatch(test: (Long) -> NetworkFilterRule?): NetworkFilterRule? {
        table.forEach { if (it != 0L) test(it - 1)?.let { rule -> return rule } }
        return null
    }
}

internal class FilterTokenIndex : java.io.Serializable {
    private var keys = LongArray(16)
    private var values = arrayOfNulls<MutableList<NetworkFilterRule>>(16)
    private var size = 0
    fun isEmpty(): Boolean = size == 0
    fun get(key: Long): List<NetworkFilterRule>? {
        var slot = tokenSlot(key, keys.size - 1)
        while (keys[slot] != 0L) {
            if (keys[slot] == key + 1) return values[slot]
            slot = (slot + 1) and (keys.size - 1)
        }
        return null
    }
    fun add(key: Long, rule: NetworkFilterRule) {
        if ((size + 1) * 2 >= keys.size) grow()
        var slot = tokenSlot(key, keys.size - 1)
        while (keys[slot] != 0L && keys[slot] != key + 1) slot = (slot + 1) and (keys.size - 1)
        if (keys[slot] == 0L) { keys[slot] = key + 1; values[slot] = mutableListOf(); size++ }
        values[slot]!!.add(rule)
    }
    fun firstMatch(test: (NetworkFilterRule) -> Boolean): NetworkFilterRule? {
        values.forEach { bucket -> bucket?.firstOrNull(test)?.let { return it } }
        return null
    }
    private fun grow() {
        val oldKeys = keys; val oldValues = values
        keys = LongArray(keys.size * 2); values = arrayOfNulls(keys.size); size = 0
        oldKeys.forEachIndexed { index, key ->
            if (key != 0L) oldValues[index]!!.forEach { add(key - 1, it) }
        }
    }
}
