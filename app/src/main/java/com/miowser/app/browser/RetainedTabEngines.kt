package com.miowser.app.browser

/** Main-owner registry. Looking up or closing a dormant record never invokes its factory. */
class RetainedTabEngines<E : Any>(private val dispose: (E) -> Unit) {
    private val engines = linkedMapOf<Long, E>()
    val size: Int get() = engines.size
    val oldestFirst: List<Long> get() = engines.keys.toList()
    fun retirementCandidates(limit: Int, eligible: (Long) -> Boolean): List<Long> =
        engines.keys.filter(eligible).take((size - limit).coerceAtLeast(0))
    fun touch(id: Long) { engines.remove(id)?.let { engines[id] = it } }
    fun peek(id: Long): E? = engines[id]
    fun acquire(id: Long, create: () -> E): E = engines[id] ?: create().also { engines[id] = it }
    fun release(id: Long) { engines.remove(id)?.let(dispose) }
    /** A delayed callback from a retired engine must never dispose its replacement. */
    fun release(id: Long, expected: E): Boolean {
        if (engines[id] !== expected) return false
        release(id)
        return true
    }
    fun releaseAll() {
        val retired = engines.values.toList()
        engines.clear()
        retired.forEach(dispose)
    }
}
