package com.miowser.app.network

/** Shared app transport budget. Two slots are reserved for playback; background work yields
 * to waiting playback. Chromium manages its own sockets outside this native transport pool. */
object ConnectionBudget {
    enum class Priority { PLAYBACK, TRANSFER, DISCOVERY, PREFETCH }
    private val monitor = Object()
    private var active = 0
    private var background = 0
    private var playbackWaiting = 0
    const val MAX_CONNECTIONS = 8
    const val MAX_BACKGROUND_CONNECTIONS = 6

    fun acquire(priority: Priority, checkActive: () -> Unit = {}): AutoCloseable {
        val playback = priority == Priority.PLAYBACK
        synchronized(monitor) {
            if (playback) playbackWaiting++
            try {
                while (true) {
                    checkActive()
                    if (active < MAX_CONNECTIONS && (playback ||
                            (background < MAX_BACKGROUND_CONNECTIONS && playbackWaiting == 0))) break
                    monitor.wait(100)
                }
                active++
                if (!playback) background++
            } finally { if (playback) playbackWaiting-- }
        }
        return object : AutoCloseable {
            private var closed = false
            override fun close() = synchronized(monitor) {
                if (!closed) {
                    closed = true
                    active--
                    if (!playback) background--
                    monitor.notifyAll()
                }
            }
        }
    }
}
