package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRecoveryCodecTest {
    @Test
    fun `round trips validators names fingerprints and ranges losslessly`() {
        val plan = DownloadRecoveryPlan(
            downloadId = 42,
            transferKind = RecoveryTransferKind.DIRECT_RANGES,
            sourceFingerprint = "sha256:value",
            etag = "\"abc:123\"",
            lastModified = "Wed, 20 Sep 2026 10:00:00 GMT",
            totalLength = 9_999,
            outputFileName = "a name\twith symbols.mp4",
            mimeType = "video/mp4",
            itemFingerprint = "items/value",
            itemCount = 2,
            elapsedActiveMillis = 42_000L,
            ranges = listOf(RecoveryRange(0, 4_999, 2_500), RecoveryRange(5_000, 9_998, 4_999))
        )

        assertEquals(plan, DownloadRecoveryCodec.decode(DownloadRecoveryCodec.encode(plan)))
    }

    @Test
    fun `rejects unknown versions malformed ranges and truncated records`() {
        val valid = DownloadRecoveryCodec.encode(
            DownloadRecoveryPlan(
                downloadId = 1,
                transferKind = RecoveryTransferKind.DIRECT_STREAM,
                sourceFingerprint = null,
                etag = null,
                lastModified = null,
                totalLength = 10,
                outputFileName = "file.bin",
                mimeType = "application/octet-stream",
                itemFingerprint = null,
                itemCount = 1,
                ranges = listOf(RecoveryRange(0, 9, 5))
            )
        )
        assertNull(DownloadRecoveryCodec.decode(valid.replaceFirst("1\t", "99\t")))
        assertNull(DownloadRecoveryCodec.decode(valid.substringBeforeLast('\t')))
        assertNull(DownloadRecoveryCodec.decode(valid.substringBeforeLast(':') + ":50"))
    }
}
