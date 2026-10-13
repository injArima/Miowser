package com.miowser.app.downloader

internal class ProgressCadence {
    private var last: Long? = null
    fun shouldPublish(nowNanos: Long, force: Boolean = false): Boolean {
        val previous = last
        if (!force && previous != null && nowNanos - previous < 250_000_000L) return false
        last = nowNanos
        return true
    }
}
