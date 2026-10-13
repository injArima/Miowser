package com.miowser.app.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputFileIdentityTest {
    @Test fun rejectsTraversalAndControls() {
        listOf("", ".", "..", "../clip.mp4", "folder\\clip.mp4", "clip\u0000.mp4", ".hidden", "clip.")
            .forEach { assertFalse(it, OutputFileIdentity.validFileName(it)) }
    }

    @Test fun acceptsOrdinaryOutputNames() {
        assertTrue(OutputFileIdentity.validFileName("My video (2).mp4"))
        assertTrue(OutputFileIdentity.validMimeType("video/mp4"))
        assertFalse(OutputFileIdentity.validMimeType("video/mp4\r\nInjected: x"))
    }
}
