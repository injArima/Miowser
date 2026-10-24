package com.miowser.app.diagnostics

import android.util.Log
import com.miowser.app.BuildConfig
import com.miowser.app.network.NativeConnections
import java.util.concurrent.atomic.AtomicInteger

/** Bounded development-only lifecycle counts; no URLs, titles or media data are logged. */
internal object DevResourceDiagnostics {
    private const val TAG = "MiowserResources"
    private val enabled = BuildConfig.BUILD_TYPE == "development"
    private val sessions = AtomicInteger()
    private val playerListeners = AtomicInteger()
    private val engines = AtomicInteger()

    fun sessionOpened() { if (enabled) { sessions.incrementAndGet(); report("player-open") } }
    fun sessionClosed() { if (enabled) { sessions.decrementAndGet(); report("player-close") } }
    fun listenerAdded() { if (enabled) { playerListeners.incrementAndGet(); report("listener-add") } }
    fun listenerRemoved() { if (enabled) { playerListeners.decrementAndGet(); report("listener-remove") } }

    fun report(phase: String, liveEngines: Int) {
        if (enabled) { engines.set(liveEngines); report(phase) }
    }

    private fun report(phase: String) {
        if (enabled) Log.d(TAG, "$phase engines=${engines.get()} sessions=${sessions.get()} " +
            "playerListeners=${playerListeners.get()} nativeRequests=${NativeConnections.activeCount}")
    }
}
