package com.miowser.app.network

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

object NativeConnections {
    private val leases = ConcurrentHashMap<HttpURLConnection, AutoCloseable>()
    private val cancellationJobs = ConcurrentHashMap<HttpURLConnection, Job>()
    val activeCount: Int get() = leases.size
    fun open(url: URL, priority: ConnectionBudget.Priority, checkActive: () -> Unit): HttpURLConnection =
        open(url, priority, checkActive, null)

    fun open(url: URL, priority: ConnectionBudget.Priority, checkActive: () -> Unit,
             owner: CoroutineContext?): HttpURLConnection {
        val lease = ConnectionBudget.acquire(priority, checkActive)
        try {
            checkActive()
            return (url.openConnection() as HttpURLConnection).also { connection ->
                leases[connection] = lease
                if (owner != null) {
                    val watcher = closeOnCancel(connection, owner)
                    cancellationJobs[connection] = watcher
                    if (!leases.containsKey(connection)) cancellationJobs.remove(connection)?.cancel()
                }
            }
        } catch (error: Throwable) { lease.close(); throw error }
    }
    fun close(connection: HttpURLConnection) {
        try { connection.disconnect() } finally {
            leases.remove(connection)?.close()
            cancellationJobs.remove(connection)?.cancel()
        }
    }

    /** A child on another dispatcher disconnects a blocking read as soon as its owner is canceled. */
    fun closeOnCancel(connection: HttpURLConnection, owner: CoroutineContext): Job =
        CoroutineScope(owner + Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { runCatching { close(connection) } }
        }

    /** Public, credential-free fetches. Each redirect gets a new budgeted connection. */
    fun openPublic(
        url: URL,
        priority: ConnectionBudget.Priority,
        checkActive: () -> Unit = {},
        owner: CoroutineContext? = null,
        onOpen: (HttpURLConnection) -> Unit = {},
        configure: (HttpURLConnection) -> Unit = {}
    ): HttpURLConnection {
        var target = url
        repeat(11) { redirects ->
            requirePublicUrl(target)
            checkActive()
            val connection = open(target, priority, checkActive, owner)
            try {
                onOpen(connection)
                connection.instanceFollowRedirects = false
                configure(connection)
                val status = connection.responseCode
                checkActive()
                if (status !in setOf(301, 302, 303, 307, 308)) return connection
                if (redirects == 10) error("Too many redirects")
                val location = connection.getHeaderField("Location") ?: error("Redirect has no destination")
                val next = URL(target, location)
                requirePublicUrl(next)
                require(target.protocol != "https" || next.protocol == "https") {
                    "Insecure redirect blocked"
                }
                target = next
            } catch (failure: Throwable) {
                close(connection)
                throw failure
            }
            close(connection)
        }
        error("Too many redirects")
    }

    private fun requirePublicUrl(url: URL) {
        require(url.protocol == "http" || url.protocol == "https") { "Unsupported URL scheme" }
        require(url.userInfo == null && url.host.isNotBlank()) { "Invalid public URL" }
    }
}
