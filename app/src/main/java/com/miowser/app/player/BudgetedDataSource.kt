package com.miowser.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.miowser.app.network.ConnectionBudget

@androidx.annotation.OptIn(UnstableApi::class)
internal class BudgetedDataSource(private val delegate: DataSource,
    private val priority: ConnectionBudget.Priority, private val checkActive: () -> Unit = {}) : DataSource by delegate {
    private var lease: AutoCloseable? = null
    private val leaseLock = Any()
    @Volatile private var closed = false
    override fun open(dataSpec: DataSpec): Long {
        closed = false
        val acquired = ConnectionBudget.acquire(priority) {
            checkActive()
            if (closed || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        }
        synchronized(leaseLock) {
            if (closed) { acquired.close(); throw java.io.InterruptedIOException() }
            lease = acquired
        }
        try {
            checkActive()
            if (closed) throw java.io.InterruptedIOException()
            val length = delegate.open(dataSpec)
            // close() can race HTTP connection establishment. Close the eventual connection too,
            // rather than letting a late successful open outlive its canceled request.
            checkActive()
            if (closed) throw java.io.InterruptedIOException()
            return length
        } catch (error: Throwable) { close(); throw error }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        return delegate.read(buffer, offset, length)
    }
    override fun close() {
        val retired = synchronized(leaseLock) {
            closed = true
            lease.also { lease = null }
        }
        try { delegate.close() } finally { retired?.close() }
    }
}
