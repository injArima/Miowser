package com.miowser.app.adblock

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** File-only storage, shared by Android callers and local migration/integrity tests. */
internal class FilterListFiles(private val directory: File) {
    fun existing(id: String): File? = zip(id).takeIf(File::isFile) ?: legacy(id).takeIf(File::isFile)

    fun install(id: String, source: () -> InputStream, checkActive: () -> Unit = {}) {
        FilterMemoryGuard().check(256L * 1024)
        val target = zip(id)
        val previousModified = existing(id)?.lastModified() ?: 0L
        check(directory.isDirectory || directory.mkdirs()) { "Could not create filter directory" }
        val temporary = File.createTempFile(target.name, ".tmp", directory)
        try {
            source().use { FilterSourceArchive.write(temporary, it, checkActive) }
            FilterSourceArchive.verify(temporary, checkActive)
            check(temporary.setLastModified(maxOf(System.currentTimeMillis(), previousModified + 1))) {
                "Could not timestamp filter source"
            }
            checkActive()
            try {
                Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            // The checked, synced ZIP is authoritative before retiring the old representation.
            legacy(id).delete()
        } finally {
            temporary.delete()
        }
    }

    fun contentEquals(id: String, candidate: File): Boolean {
        val file = existing(id) ?: return false
        return try {
            open(file).buffered().use { left ->
                FilterSourceArchive.openPlain(candidate).buffered().use { right ->
                    streamsEqual(left, right)
                }
            }
        } catch (_: Exception) { false }
    }

    fun delete(id: String) {
        zip(id).delete()
        legacy(id).delete()
    }

    private fun zip(id: String) = File(directory, "list-${safeId(id)}.zip")
    private fun legacy(id: String) = File(directory, "list-${safeId(id)}.txt")
    private fun safeId(id: String): String = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .also { require(it.isNotBlank()) { "Invalid subscription id" } }

    companion object {
        fun open(file: File): InputStream = if (file.extension == "zip") FilterSourceArchive.open(file)
            else FilterSourceArchive.openPlain(file)

        fun reader(file: File): BufferedReader = open(file).bufferedReader(Charsets.UTF_8)

        private fun streamsEqual(left: InputStream, right: InputStream): Boolean {
            val a = ByteArray(32 * 1024)
            val b = ByteArray(32 * 1024)
            while (true) {
                val count = readChunk(left, a)
                if (count != readChunk(right, b)) return false
                if (count == 0) return true
                for (index in 0 until count) if (a[index] != b[index]) return false
            }
        }

        private fun readChunk(input: InputStream, buffer: ByteArray): Int {
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            return count
        }
    }
}
