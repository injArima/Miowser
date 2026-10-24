package com.miowser.app.adblock

/** Small synchronized LRU; expensive computations happen outside the monitor. */
internal class BoundedFilterCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val maxWeight: Int = Int.MAX_VALUE,
    private val weigh: (K, V) -> Int = { _, _ -> 1 }
) {
    private val values = LinkedHashMap<K, V>(16, 0.75f, true)
    private var weight = 0
    @Synchronized fun get(key: K): V? = values[key]
    @Synchronized fun put(key: K, value: V) {
        val cost = weigh(key, value)
        if (cost > maxWeight) return
        values.remove(key)?.let { weight -= weigh(key, it) }
        values[key] = value
        weight += cost
        while (values.size > maxEntries || weight > maxWeight) {
            val oldest = values.entries.iterator()
            val entry = oldest.next()
            weight -= weigh(entry.key, entry.value)
            oldest.remove()
        }
    }
    @Synchronized fun clear() { values.clear(); weight = 0 }
}
