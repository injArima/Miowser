package com.miowser.app.performance

/** Bounded access-order cache. Weights include retained keys; expensive work stays outside it. */
internal class WeightedCache<K : Any, V : Any>(private val maxEntries: Int, private val maxBytes: Int,
    private val weightOf: (K, V) -> Int) {
    private val values = LinkedHashMap<K, V>(16, 0.75f, true)
    private var bytes = 0L
    @Synchronized operator fun get(key: K): V? = values[key]
    @Synchronized operator fun set(key: K, value: V) {
        remove(key)
        val weight = weightOf(key, value).coerceAtLeast(0)
        if (weight > maxBytes) return
        values[key] = value; bytes += weight
        while (values.size > maxEntries || bytes > maxBytes) {
            val entry = values.entries.iterator().next()
            remove(entry.key)
        }
    }
    @Synchronized fun remove(key: K) { values.remove(key)?.let { bytes -= weightOf(key, it).coerceAtLeast(0) } }
    @Synchronized fun size(): Int = values.size
    @Synchronized fun retainedBytes(): Long = bytes
}

/** Power-of-two downsampling is selected from image bounds, before any pixel allocation. */
internal fun boundedImageSample(width: Int, height: Int, targetEdge: Int): Int? {
    if (width <= 0 || height <= 0 || width > 32_768 || height > 32_768 || width.toLong() * height > 100_000_000) return null
    var sample = 1
    while (width / sample > targetEdge || height / sample > targetEdge) sample *= 2
    return sample
}
