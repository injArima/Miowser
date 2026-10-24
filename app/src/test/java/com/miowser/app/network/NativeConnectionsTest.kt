package com.miowser.app.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeConnectionsTest {
    @Test fun publicRedirectClosesPreviousLeaseAndKeepsOnlyPublicHeaders() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/destination")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/destination") { exchange ->
            assertEquals("Miowser test", exchange.requestHeaders.getFirst("User-Agent"))
            assertEquals(null, exchange.requestHeaders.getFirst("Cookie"))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val opened = mutableListOf<java.net.HttpURLConnection>()
            val connection = NativeConnections.openPublic(
                URL("http://127.0.0.1:${server.address.port}/redirect"),
                ConnectionBudget.Priority.DISCOVERY,
                onOpen = { opened += it },
                configure = { it.setRequestProperty("User-Agent", "Miowser test") }
            )
            assertEquals(200, connection.responseCode)
            assertEquals(2, opened.size)
            NativeConnections.close(connection)
            ConnectionBudget.acquire(ConnectionBudget.Priority.DISCOVERY).close()
        } finally { server.stop(0) }
    }

    @Test fun invalidRedirectReleasesConnectionBudget() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", "file:///private/data")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            val result = runCatching {
                NativeConnections.openPublic(
                    URL("http://127.0.0.1:${server.address.port}/"),
                    ConnectionBudget.Priority.DISCOVERY
                )
            }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            val leases = (0 until ConnectionBudget.MAX_BACKGROUND_CONNECTIONS).map {
                ConnectionBudget.acquire(ConnectionBudget.Priority.DISCOVERY)
            }
            leases.forEach(AutoCloseable::close)
        } finally { server.stop(0) }
    }

    @Test fun cancelingOwnerDisconnectsActiveConnection() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requestArrived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        server.createContext("/") { exchange ->
            requestArrived.countDown()
            releaseResponse.await(2, TimeUnit.SECONDS)
            runCatching { exchange.sendResponseHeaders(200, -1) }
            exchange.close()
        }
        server.start()
        val owner = Job()
        try {
            val connection = NativeConnections.open(
                URL("http://127.0.0.1:${server.address.port}/"),
                ConnectionBudget.Priority.DISCOVERY,
                checkActive = {},
                owner = owner
            )
            val finished = AtomicBoolean(false)
            val reader = Thread {
                runCatching { connection.inputStream.use { it.read() } }
                finished.set(true)
            }
            reader.start()
            assertTrue(requestArrived.await(2, TimeUnit.SECONDS))
            owner.cancel()
            reader.join(2000)
            assertTrue(finished.get())
            NativeConnections.close(connection)
        } finally {
            releaseResponse.countDown()
            owner.cancel()
            server.stop(0)
        }
    }
}
