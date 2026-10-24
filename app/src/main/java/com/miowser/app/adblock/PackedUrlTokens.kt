package com.miowser.app.adblock

import java.io.DataInputStream
import java.io.DataOutputStream

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
internal class PackedUrlTokens(url: String, width: Int = 5) {
    private val table: LongArray
    init {
        require(width in 1..5)
        var capacity = 16
        while (capacity < (url.length - width + 1).coerceAtLeast(0) * 2) capacity *= 2
        table = LongArray(capacity)
        var token = 0L
        val mask = (1L shl (width * 8)) - 1
        url.forEachIndexed { index, c ->
            val folded = if (c in 'A'..'Z') c.code + 32 else c.code
            token = ((token shl 8) or folded.toLong()) and mask
            if (index >= width - 1) {
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
    fun forEachRule(action: (NetworkFilterRule) -> Unit) {
        values.forEach { bucket -> bucket?.forEach(action) }
    }
    fun get(key: Long): List<NetworkFilterRule>? {
        var slot = tokenSlot(key, keys.size - 1)
        while (keys[slot] != 0L) {
            if (keys[slot] == key + 1) return values[slot]
            slot = (slot + 1) and (keys.size - 1)
        }
        return null
    }
    fun add(key: Long, rule: NetworkFilterRule, memory: FilterMemoryGuard? = null) {
        var slot = tokenSlot(key, keys.size - 1)
        while (keys[slot] != 0L && keys[slot] != key + 1) slot = (slot + 1) and (keys.size - 1)
        if (keys[slot] == 0L && (size + 1) * 2 >= keys.size) {
            grow(memory)
            slot = tokenSlot(key, keys.size - 1)
            while (keys[slot] != 0L) slot = (slot + 1) and (keys.size - 1)
        }
        if (keys[slot] == 0L) { keys[slot] = key + 1; values[slot] = mutableListOf(); size++ }
        values[slot]!!.add(rule)
    }
    fun firstMatch(test: (NetworkFilterRule) -> Boolean): NetworkFilterRule? {
        values.forEach { bucket -> bucket?.firstOrNull(test)?.let { return it } }
        return null
    }

    /** Persist physical slots as well as bucket order; restore never selects tokens or rehashes. */
    internal fun writeSnapshot(output: DataOutputStream, writeRule: (NetworkFilterRule) -> Unit) {
        output.writeInt(keys.size)
        output.writeInt(size)
        keys.forEachIndexed { slot, key ->
            output.writeLong(key)
            if (key != 0L) {
                val bucket = values[slot]!!
                output.writeInt(bucket.size)
                bucket.forEach(writeRule)
            }
        }
    }

    internal fun readSnapshot(input: DataInputStream, ruleCount: Int, width: Int,
                              memory: FilterMemoryGuard, readRule: () -> NetworkFilterRule) {
        check(size == 0) { "Token index must be empty before restore" }
        val capacity = input.readInt()
        require(capacity >= 16 && capacity and (capacity - 1) == 0 &&
            capacity.toLong() <= maxOf(16L, (ruleCount.toLong() + 1) * 4)) { "Invalid token table capacity" }
        val expectedSize = input.readInt()
        require(expectedSize in 0 until capacity / 2 && expectedSize <= ruleCount) { "Invalid token bucket count" }
        memory.check(capacity.toLong() * 16 + 64)
        val restoredKeys = LongArray(capacity)
        val restoredValues = arrayOfNulls<MutableList<NetworkFilterRule>>(capacity)
        var buckets = 0
        var rules = 0L
        for (slot in 0 until capacity) {
            if (slot % 256 == 0) memory.check()
            val key = input.readLong()
            restoredKeys[slot] = key
            if (key == 0L) continue
            require(key > 0 && (key - 1) ushr (width * 8) == 0L) { "Invalid packed token" }
            val count = input.readInt()
            rules += count.toLong()
            require(count > 0 && rules <= ruleCount && ++buckets <= expectedSize) { "Invalid token rule count" }
            memory.check(count.toLong() * 8 + 32)
            restoredValues[slot] = ArrayList<NetworkFilterRule>(count).apply {
                repeat(count) {
                    val rule = readRule()
                    check(rule.anchoredDomain == null) { "Domain rule in token index" }
                    add(rule)
                }
            }
        }
        check(buckets == expectedSize) { "Missing token buckets" }
        // Check open-addressing invariants without inserting keys or reselecting rule tokens.
        restoredKeys.forEachIndexed { position, key ->
            if (key != 0L) {
                var slot = tokenSlot(key - 1, capacity - 1)
                while (slot != position && restoredKeys[slot] != 0L) {
                    check(restoredKeys[slot] != key) { "Duplicate token key" }
                    slot = (slot + 1) and (capacity - 1)
                }
                check(slot == position) { "Unreachable token bucket" }
            }
        }
        keys = restoredKeys
        values = restoredValues
        size = buckets
    }
    private fun grow(memory: FilterMemoryGuard?) {
        val oldKeys = keys; val oldValues = values
        memory?.check(keys.size.toLong() * 2 * 16 + 64)
        keys = LongArray(keys.size * 2); values = arrayOfNulls(keys.size); size = 0
        oldKeys.forEachIndexed { index, key ->
            if (key != 0L) {
                var slot = tokenSlot(key - 1, keys.size - 1)
                while (keys[slot] != 0L) slot = (slot + 1) and (keys.size - 1)
                keys[slot] = key
                values[slot] = oldValues[index]
                size++
            }
        }
    }
}
