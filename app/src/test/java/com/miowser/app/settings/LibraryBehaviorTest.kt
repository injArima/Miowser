package com.miowser.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBehaviorTest {
    @Test
    fun automaticRecordsRegularPlayback() {
        assertTrue(shouldRecordLibraryPlayback(
            LibraryBehavior.AUTOMATIC,
            openedFromLibrary = false,
            privateTab = false
        ))
    }

    @Test
    fun automaticDoesNotRecordPrivatePlayback() {
        assertFalse(shouldRecordLibraryPlayback(
            LibraryBehavior.AUTOMATIC,
            openedFromLibrary = false,
            privateTab = true
        ))
    }

    @Test
    fun manualOnlyDoesNotRecordUnsavedPlayback() {
        assertFalse(shouldRecordLibraryPlayback(
            LibraryBehavior.MANUAL_ONLY,
            openedFromLibrary = false,
            privateTab = false
        ))
    }

    @Test
    fun manualOnlyUpdatesAnExistingLibraryEntry() {
        assertTrue(shouldRecordLibraryPlayback(
            LibraryBehavior.MANUAL_ONLY,
            openedFromLibrary = true,
            privateTab = false
        ))
    }

    @Test
    fun disabledNeverRecordsPlayback() {
        assertFalse(shouldRecordLibraryPlayback(
            LibraryBehavior.DISABLED,
            openedFromLibrary = true,
            privateTab = false
        ))
    }
}
