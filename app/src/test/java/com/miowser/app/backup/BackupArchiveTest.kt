package com.miowser.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    @Test fun compressedAndLegacyFilterSourcesSurviveBackup() {
        val root = Files.createTempDirectory("miowser-filter-backup").toFile()
        try {
            val sources = com.miowser.app.adblock.FilterListFiles(File(root, "sources"))
            sources.install("example", { "||ads.example^\n".byteInputStream() })
            val archive = ByteArrayOutputStream()
            BackupArchive.write(archive, listOf(
                BackupSource("files/adblock/list-example.zip") { sources.existing("example")!!.inputStream() },
                BackupSource("files/adblock/list-legacy.txt") { "||legacy.example^\n".byteInputStream() }
            ), 123L)
            val restored = BackupArchive.read(archive.toByteArray().inputStream(), File(root, "restored"))
            assertEquals("||ads.example^\n", com.miowser.app.adblock.FilterListFiles.reader(
                restored.files.getValue("files/adblock/list-example.zip")).use { it.readText() })
            assertEquals("||legacy.example^\n", restored.files.getValue("files/adblock/list-legacy.txt").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun damagedNestedFilterArchiveIsRejectedBeforeRestore() {
        val root = Files.createTempDirectory("miowser-filter-bad-backup").toFile()
        try {
            val archive = ByteArrayOutputStream()
            // The outer manifest/checksum is valid. Its nested filter source is not.
            BackupArchive.write(archive, listOf(
                BackupSource("files/adblock/list-example.zip") { "not a ZIP".byteInputStream() }
            ), 123L)
            val destination = File(root, "restored")
            assertThrows(Exception::class.java) { BackupArchive.read(archive.toByteArray().inputStream(), destination) }
            assertFalse(destination.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun srtCaptionsAndArtworkOmissionArePreserved() {
        val name = "files/library_subtitles/${"b".repeat(64)}.srt"
        val caption = "1\n00:00:01,000 --> 00:00:02,000\nSaved subtitle\n"
        val bytes = ByteArrayOutputStream()
        BackupArchive.write(bytes, listOf(BackupSource(name) { caption.byteInputStream() }), 123L, 17)
        val directory = Files.createTempDirectory("miowser-srt-backup").toFile()
        try {
            val restored = BackupArchive.read(bytes.toByteArray().inputStream(), directory)
            assertEquals(caption, restored.files.getValue(name).readText())
            assertEquals(17, restored.summary.skippedArtwork)
        } finally { directory.deleteRecursively() }
    }

    @Test fun legacyVersionOneManifestRemainsReadable() {
        val original = ByteArrayOutputStream()
        BackupArchive.write(original, listOf(BackupSource("prefs/settings.json") { "{}".byteInputStream() }), 123L)
        val legacy = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(legacy).use { output ->
            java.util.zip.ZipInputStream(original.toByteArray().inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    var payload = input.readBytes()
                    if (entry.name == "manifest.properties") {
                        val properties = java.util.Properties().apply { load(payload.inputStream()); setProperty("version", "1"); remove("skippedArtwork") }
                        payload = ByteArrayOutputStream().also { properties.store(it, "legacy") }.toByteArray()
                    }
                    output.putNextEntry(java.util.zip.ZipEntry(entry.name)); output.write(payload); output.closeEntry()
                }
            }
        }
        val directory = Files.createTempDirectory("miowser-legacy-backup").toFile()
        try {
            val restored = BackupArchive.read(legacy.toByteArray().inputStream(), directory)
            assertEquals("{}", restored.files.getValue("prefs/settings.json").readText())
            assertEquals(0, restored.summary.skippedArtwork)
        } finally { directory.deleteRecursively() }
    }

    @Test fun `round trip preserves bounded settings and assets`() {
        val output = ByteArrayOutputStream()
        val original = "{\"bookmarks\":\"example\"}".toByteArray()
        val summary = BackupArchive.write(output, listOf(
            BackupSource("prefs/bookmarks.json") { ByteArrayInputStream(original) },
            BackupSource("files/adblock/manual-rules.txt") { ByteArrayInputStream("rule".toByteArray()) }
        ), 123L)
        val directory = Files.createTempDirectory("miowser-restore-test").toFile()
        try {
            val restored = BackupArchive.read(ByteArrayInputStream(output.toByteArray()), directory)
            assertEquals(summary, restored.summary)
            assertEquals(String(original), restored.files.getValue("prefs/bookmarks.json").readText())
            assertEquals("rule", restored.files.getValue("files/adblock/manual-rules.txt").readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun `unsafe archive paths are rejected`() {
        assertFalse(allowedBackupPath("files/../secret"))
        assertFalse(allowedBackupPath("files/adblock/../../private_session.xml"))
        assertFalse(allowedBackupPath("files/adblock/compiled-v3.bin"))
        assertTrue(allowedBackupPath("files/adblock/list-example.zip"))
        assertFalse(allowedBackupPath("files/adblock/arbitrary.zip"))
        assertFalse(allowedBackupPath("prefs/private_session.json"))
        assertFalse(allowedBackupPath("prefs/history.json"))
        assertFalse(allowedBackupPath("prefs/browser_session.json"))
        assertTrue(allowedBackupPath("files/library_subtitles/${"a".repeat(64)}.vtt"))
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.write(ByteArrayOutputStream(), listOf(
                BackupSource("../escape") { ByteArrayInputStream(byteArrayOf(1)) }
            ), 123L)
        }
    }

    @Test fun `truncated backup is rejected without leaving staged files`() {
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, listOf(
            BackupSource("prefs/settings.json") { ByteArrayInputStream("{}".toByteArray()) }
        ), 123L)
        val bytes = output.toByteArray().copyOf(output.size() / 2)
        val directory = File(Files.createTempDirectory("miowser-bad-archive").toFile(), "staged")
        assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(bytes), directory) }
        assertFalse(directory.exists())
        directory.parentFile?.deleteRecursively()
    }
}
