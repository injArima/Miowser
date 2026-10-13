package com.miowser.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DurableRestoreTransactionTest {
    @Test fun partialApplicationReplaysVerifiedGenerationBeforeStartup() {
        val sandbox = Files.createTempDirectory("miowser-restore-test").toFile()
        val journal = DurableRestoreTransaction(File(sandbox, "journal"))
        val values = linkedMapOf("prefs/settings.json" to "new settings", "prefs/media_library.json" to "new library")
        val live = File(sandbox, "live").apply { mkdirs() }
        try {
            values.keys.forEach { File(live, it.substringAfter('/')).writeText("old") }
            journal.prepare(values.map { (key, value) -> BackupSource(key) { value.byteInputStream() } }, 123)
            assertTrue(journal.needsRecovery)
            assertTrue(runCatching { journal.recover { prepared ->
                val first = prepared.files.entries.first()
                first.value.copyTo(File(live, first.key.substringAfter('/')), overwrite = true)
                error("simulated process interruption")
            } }.isFailure)
            assertTrue(journal.needsRecovery)
            journal.recover { prepared -> prepared.files.forEach { (key, source) ->
                source.copyTo(File(live, key.substringAfter('/')), overwrite = true)
            } }
            values.forEach { (key, value) -> assertEquals(value, File(live, key.substringAfter('/')).readText()) }
            assertFalse(journal.needsRecovery)
            journal.recover { error("already committed") }
        } finally { sandbox.deleteRecursively() }
    }

    @Test fun incompleteStagingDoesNotChangeLiveData() {
        val root = Files.createTempDirectory("miowser-restore-stage").toFile()
        try {
            File(root, "generation.zip").writeText("incomplete")
            DurableRestoreTransaction(root).recover { error("uncommitted staging must not apply") }
            assertFalse(root.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptReadyGenerationRemainsJournaledForRecovery() {
        val root = Files.createTempDirectory("miowser-restore-corrupt").toFile()
        try {
            val journal = DurableRestoreTransaction(root)
            journal.prepare(listOf(BackupSource("prefs/settings.json") { "new".byteInputStream() }), 123)
            File(root, "generation.zip").writeText("corrupt")
            assertTrue(runCatching { journal.recover { error("must not apply") } }.isFailure)
            assertTrue(journal.needsRecovery)
        } finally { root.deleteRecursively() }
    }
}
