package com.miowser.app.network

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object NativeConnections {
    private val leases = ConcurrentHashMap<HttpURLConnection, AutoCloseable>()
    fun open(url: URL, priority: ConnectionBudget.Priority, checkActive: () -> Unit): HttpURLConnection {
        val lease = ConnectionBudget.acquire(priority, checkActive)
        try {
            checkActive()
            return (url.openConnection() as HttpURLConnection).also { leases[it] = lease }
        } catch (error: Throwable) { lease.close(); throw error }
    }
    fun close(connection: HttpURLConnection) {
        try { connection.disconnect() } finally { leases.remove(connection)?.close() }
    }
}
