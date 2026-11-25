package com.miowser.app.downloader

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TransferPolicyTest {
    @Test fun rejectsEarlyAndOverlongEofButAcceptsExactAndUnknownLengths() {
        for (received in listOf(99L, 101L)) {
            val error = runCatching { TransferPolicy.requireComplete(100, received) }.exceptionOrNull()!!
            assertEquals(TransferFailureKind.NETWORK, TransferPolicy.from(error).kind)
        }
        TransferPolicy.requireComplete(100, 100)
        TransferPolicy.requireComplete(0, 0)
        TransferPolicy.requireComplete(-1, 800)
    }

    @Test fun honorsServerWaitSecondsAndHttpDate() {
        val now = java.time.Instant.parse("2026-11-26T00:00:00Z").toEpochMilli()
        assertEquals(90_000L, TransferPolicy.http(429, "90", now).retryAfterMillis)
        assertEquals(120_000L, TransferPolicy.http(503, "Thu, 26 Nov 2026 00:02:00 GMT", now).retryAfterMillis)
        assertEquals(0L, TransferPolicy.retryAfterMillis("Wed, 25 Nov 2026 23:59:00 GMT", now))
        assertEquals(0L, TransferPolicy.retryAfterMillis("invalid", now))
        assertEquals(90_000L, TransferPolicy.retryDelay(TransferPolicy.http(429, "90", now), 1))
    }

    @Test fun onlyTemporaryFailuresRetryAndWrappedStatusSurvives() {
        listOf(401, 403, 404, 410, 416, 501).forEach { assertFalse(TransferPolicy.http(it).retryable) }
        listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(TransferPolicy.http(it).retryable) }
        val error = IOException("range failed", TransferException(TransferPolicy.http(403)))
        assertEquals(TransferFailureKind.AUTHORIZATION, TransferPolicy.from(error).kind)
        assertFalse(TransferPolicy.from(IllegalArgumentException("Unsupported manifest")).retryable)
        assertFalse(TransferPolicy.from(SecurityException("Denied")).retryable)
        assertFalse(TransferPolicy.from(IOException("No space left on device")).retryable)
        assertTrue(TransferPolicy.from(java.net.SocketTimeoutException("Timed out")).retryable)
        assertFalse(TransferPolicy.from(javax.net.ssl.SSLHandshakeException("Certificate")).retryable)
    }

    @Test fun parallelRangesShareOneRetryBudget() {
        val download = Download(1, "https://example.test/video", "https://example.test", "ua", "", null,
            DownloadKind.DIRECT_FILE, "file", "file")
        val pool = Executors.newFixedThreadPool(6)
        try {
            val attempts = (1..20).map {
                pool.submit<Boolean> { download.nextRetry(TransferPolicy.from(IOException("offline"))) != null }
            }.count { it.get(2, TimeUnit.SECONDS) }
            assertEquals(3, attempts)
            assertEquals(3, download.retryCount)
            assertNull(download.nextRetry(TransferPolicy.http(429)))
        } finally { pool.shutdownNow() }
    }

    @Test fun effectiveConnectionsRespectHostAndSharedBudget() {
        assertEquals(6, effectiveDownloadConnections(8, 8, 6))
        assertEquals(2, effectiveDownloadConnections(0, 6, 2))
        assertEquals(1, effectiveDownloadConnections(1, 4, 6))
        assertEquals(2, effectiveDownloadConnections(2, 4, 6))
    }

    @Test fun storageHeadroomIncludesOtherJobsAndCannotOverflow() {
        assertEquals(300 + DOWNLOAD_SPACE_RESERVE, requiredDownloadHeadroom(100, 200))
        assertEquals(DOWNLOAD_SPACE_RESERVE, requiredDownloadHeadroom(-1, 0))
        assertEquals(Long.MAX_VALUE, requiredDownloadHeadroom(Long.MAX_VALUE, 1))
        assertEquals(Long.MAX_VALUE, requiredDownloadHeadroom(1, Long.MAX_VALUE))
    }
}
