package com.miowser.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class AppearanceBackupTest {
    @Test fun appearanceIsAllowedAndOlderCompleteArchivesRemainSupported() {
        assertTrue(allowedBackupPath("prefs/appearance.json"))
        assertTrue(supportedBackupPreferenceNames(BACKUP_PREFERENCE_NAMES.toSet()))
        assertTrue(supportedBackupPreferenceNames((BACKUP_PREFERENCE_NAMES - "appearance").toSet()))
        assertFalse(supportedBackupPreferenceNames((BACKUP_PREFERENCE_NAMES - "settings").toSet()))
        assertFalse(supportedBackupPreferenceNames(BACKUP_PREFERENCE_NAMES.toSet() + "private_session"))
    }

    @Test fun archivePreservesTheNamedPaletteAndMode() {
        val payload = """{"theme_mode":"AMOLED","color_theme":"BLACK_GOLD"}"""
        val bytes = ByteArrayOutputStream()
        BackupArchive.write(bytes, listOf(BackupSource("prefs/appearance.json") { payload.byteInputStream() }), 123L)
        val directory = Files.createTempDirectory("miowser-appearance-backup").toFile()
        try {
            val restored = BackupArchive.read(bytes.toByteArray().inputStream(), directory)
            assertEquals(payload, restored.files.getValue("prefs/appearance.json").readText())
            restored.discard()
        } finally { directory.deleteRecursively() }
    }
}
