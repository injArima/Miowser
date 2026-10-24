package com.miowser.app.adblock

/** Leave room for the UI and page requests while a replacement ruleset is being built. */
internal class FilterMemoryGuard(
    private val reserveBytes: Long = (Runtime.getRuntime().maxMemory() / 8)
        .coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024),
    private val availableBytes: () -> Long = {
        val runtime = Runtime.getRuntime()
        runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }
) {
    fun check(additionalBytes: Long = 0) {
        require(additionalBytes >= 0)
        if (availableBytes() - additionalBytes < reserveBytes) throw FilterMemoryPressureException()
    }
}

internal class FilterMemoryPressureException : IllegalStateException(
    "Not enough memory to update filter lists safely; previous protection retained"
)
