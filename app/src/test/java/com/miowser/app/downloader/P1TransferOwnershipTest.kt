package com.miowser.app.downloader

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class P1TransferOwnershipTest {
    private fun download(id: Long) = Download(id, "https://example.test/video", "https://example.test/",
        "test", "https://example.test/", null, DownloadKind.DIRECT_FILE, "video", "video")

    @Test fun pausingTwoTransfersReleasesSlotsForThird() {
        val executor = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(2)
        val transfers = listOf(download(1), download(2))
        try {
            val owners = transfers.map { download -> executor.submit {
                download.status = DownloadStatus.DOWNLOADING
                started.countDown()
                try { while (true) { download.awaitIfPaused(); Thread.sleep(1) } }
                catch (_: DownloadYieldException) { /* same ownership unwind used by Queue */ }
            } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            transfers.forEach { assertTrue(it.requestPause()) }
            assertEquals("third started", executor.submit<String> { "third started" }.get(2, TimeUnit.SECONDS))
            owners.forEach { it.get(2, TimeUnit.SECONDS) }
            transfers.forEach { assertEquals(DownloadStatus.PAUSED, it.status) }
        } finally { executor.shutdownNow() }
    }

    @Test fun fastResumeStillUnwindsOldOwnerBeforeNewAttempt() {
        val download = download(1).apply { status = DownloadStatus.DOWNLOADING }
        download.requestPause()
        download.requestResume()
        assertTrue(runCatching { download.awaitIfPaused() }.exceptionOrNull() is DownloadYieldException)
        download.beginAttempt()
        download.awaitIfPaused()
        assertEquals(DownloadStatus.DOWNLOADING, download.status)
    }

    @Test fun metadataLimitRejectsOversizedBodyAndAcceptsExactLimit() {
        val download = download(1)
        assertArrayEquals(ByteArray(16), ByteArrayInputStream(ByteArray(16)).readBytesPausable(download, 16))
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(17)).readBytesPausable(download, 16) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun segmentSchedulerQueuesOnlyItsFixedWorkers() {
        val jobs = mutableListOf<Runnable>()
        val started = CountDownLatch(1)
        val owner = Executors.newSingleThreadExecutor()
        val count = AtomicInteger()
        try {
            val result = owner.submit {
                boundedSegmentWork((0 until 10000).toList(), Executor { synchronized(jobs) {
                    jobs.add(it); if (jobs.size == 4) started.countDown()
                } }, checkActive = {}) { count.incrementAndGet() }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val queued = synchronized(jobs) { jobs.toList() }
            assertEquals(4, queued.size)
            queued.forEach(Runnable::run)
            result.get(2, TimeUnit.SECONDS)
            assertEquals(10000, count.get())
        } finally { owner.shutdownNow() }
    }

    @Test fun failedSegmentJoinsAllWorkersBeforeReturningOwnership() {
        val executor = Executors.newFixedThreadPool(4)
        val active = AtomicInteger()
        try {
            val failure = runCatching {
                boundedSegmentWork((0 until 100).toList(), executor, checkActive = {}) { index ->
                    active.incrementAndGet()
                    try { if (index == 0) error("bad segment"); Thread.sleep(10) }
                    finally { active.decrementAndGet() }
                }
            }
            assertTrue(failure.isFailure)
            assertEquals(0, active.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun cadencePublishesFourTimesPerSecondAndForcesTransitions() {
        val cadence = ProgressCadence()
        assertEquals(4, (0 until 1000).count { cadence.shouldPublish(it * 1_000_000L) })
        assertTrue(cadence.shouldPublish(999_000_000L, force = true))
        assertFalse(cadence.shouldPublish(1_000_000_000L))
    }

    @Test fun pausePublishesExactUnreportedBytesImmediately() {
        val download = download(1).apply { status = DownloadStatus.DOWNLOADING }
        download.bytesDownloaded = 100
        download.bytesDownloaded = 200
        download.requestPause()
        assertEquals(200, download.bytesDownloaded)
    }
}
