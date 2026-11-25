package com.miowser.app.network

/** Shared app transport budget. Two slots are reserved for playback; background work yields
 * to waiting playback. Chromium manages its own sockets outside this native transport pool. */
object ConnectionBudget {
    enum class Priority { PLAYBACK, TRANSFER, DISCOVERY, PREFETCH }
    private val monitor = Object()
    private var active = 0
    private var background = 0
    private var playbackWaiting = 0
    private val transfersByHost = mutableMapOf<String, Int>()
    const val MAX_CONNECTIONS = 8
    const val MAX_BACKGROUND_CONNECTIONS = 6

    fun acquire(priority: Priority, checkActive: () -> Unit = {}): AutoCloseable =
        acquire(priority, checkActive, null, MAX_BACKGROUND_CONNECTIONS)

    fun acquire(priority: Priority, checkActive: () -> Unit, host: String?, perHostLimit: Int): AutoCloseable {
        val playback = priority == Priority.PLAYBACK
        val transferHost = host?.lowercase()?.takeIf { priority == Priority.TRANSFER && it.isNotBlank() }
        synchronized(monitor) {
            if (playback) playbackWaiting++
            try {
                while (true) {
                    checkActive()
                    if (active < MAX_CONNECTIONS && (playback ||
                            (background < MAX_BACKGROUND_CONNECTIONS && playbackWaiting == 0)) &&
                        (transferHost == null || transfersByHost.getOrDefault(transferHost, 0) < perHostLimit.coerceIn(1, MAX_BACKGROUND_CONNECTIONS))) break
                    monitor.wait(100)
                }
                active++
                if (!playback) background++
                if (transferHost != null) transfersByHost[transferHost] = transfersByHost.getOrDefault(transferHost, 0) + 1
            } finally { if (playback) playbackWaiting-- }
        }
        return object : AutoCloseable {
            private var closed = false
            override fun close() = synchronized(monitor) {
                if (!closed) {
                    closed = true
                    active--
                    if (!playback) background--
                    if (transferHost != null) {
                        val count = transfersByHost.getOrDefault(transferHost, 1) - 1
                        if (count == 0) transfersByHost.remove(transferHost) else transfersByHost[transferHost] = count
                    }
                    monitor.notifyAll()
                }
            }
        }
    }
}
