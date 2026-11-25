package com.miowser.app.downloader

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class FileVerificationTest {
    @Test fun hashesSavedBytesAndComparesExpectedHashes() {
        val hashes = FileVerification.hash(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashes.sha256)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hashes.md5)
        assertEquals(3L, hashes.bytes)
        assertEquals(true, hashes.matches("  ${hashes.sha256.uppercase()} "))
        assertEquals(true, hashes.matches(hashes.md5))
        assertEquals(false, hashes.matches("0".repeat(64)))
        assertNull(hashes.matches("not a hash"))
    }

    @Test fun handlesEmptyFileAndStopsWhenDialogIsCanceled() {
        val empty = FileVerification.hash(ByteArrayInputStream(byteArrayOf()))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", empty.md5)
        var checks = 0
        val error = runCatching {
            FileVerification.hash(ByteArrayInputStream(ByteArray(200_000))) {
                if (++checks == 2) throw CancellationException("Closed")
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
