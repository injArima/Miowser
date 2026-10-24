package com.miowser.app.adblock

import org.junit.Assert.assertTrue
import org.junit.Test

class FilterMemoryGuardTest {
    @Test
    fun `large snapshot array must leave reserved headroom`() {
        val guard = FilterMemoryGuard(reserveBytes = 20L, availableBytes = { 30L })
        guard.check(10)
        assertTrue(runCatching { guard.check(11) }.exceptionOrNull() is FilterMemoryPressureException)
        assertTrue(runCatching { guard.check(Long.MAX_VALUE) }.exceptionOrNull() is FilterMemoryPressureException)
    }

    @Test
    fun `stops background work with a normal failure before exhausting the heap`() {
        var headroom = 30L
        val guard = FilterMemoryGuard(reserveBytes = 20L, availableBytes = { headroom })
        guard.check()
        headroom = 19L
        val error = runCatching { guard.check() }.exceptionOrNull()
        assertTrue(error is FilterMemoryPressureException)
    }
}
