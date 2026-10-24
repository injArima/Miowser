package com.miowser.app.persistence

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

/** Process-lifetime owner: leaving a screen does not cancel an accepted save. */
object Persistence {
    private val main = Handler(Looper.getMainLooper())
    var state by mutableStateOf(WriteState())
        private set
    private val writer = OrderedWriter(Executors.newSingleThreadExecutor { task ->
        Thread(task, "Miowser-persistence")
    }) { next -> main.post { state = next } }

    fun requireOwner() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "Repository edits and snapshots must run on the main owner"
    }

    fun onOwner(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    fun submit(key: String, write: () -> Boolean): java.util.concurrent.CompletableFuture<Boolean> {
        if (RestoreWriteFence.frozen) return java.util.concurrent.CompletableFuture.completedFuture(false)
        return writer.submit(key) { RestoreWriteFence.write(write) ?: true }
    }
    fun submitSnapshot(key: String, write: () -> Boolean): java.util.concurrent.CompletableFuture<Boolean> {
        if (RestoreWriteFence.frozen) return java.util.concurrent.CompletableFuture.completedFuture(false)
        return writer.submitSnapshot(key) { RestoreWriteFence.write(write) ?: true }
    }
    fun metrics() = writer.metrics()
    fun recordEncoded(value: String) {
        var bytes = 0L
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            bytes += when {
                c.code < 128 -> 1
                c.code < 2048 -> 2
                c.isHighSurrogate() && i < value.length && value[i].isLowSurrogate() -> { i++; 4 }
                c.isSurrogate() -> 1
                else -> 3
            }
        }
        writer.recordEncodedBytes(bytes)
    }
    fun retryFailed() = writer.retryFailed()
    fun afterWrites(onSuccess: () -> Unit) {
        writer.awaitCommitted().thenRun { main.post { onSuccess() } }
    }
}
