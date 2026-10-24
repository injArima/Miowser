package com.miowser.app.adblock

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal class FilterSourceIntegrityException(message: String, cause: Exception? = null) : IOException(message, cause)

/** A private source container, never opened by request matching. No extraction or full-body reads. */
internal object FilterSourceArchive {
    const val ENTRY_NAME = "filters.txt"
    // DEFLATE and ZIP headers can make an incompressible 32 MiB source slightly larger.
    const val MAX_ARCHIVE_BYTES = FilterLimits.LIST_BYTES.toLong() + 64 * 1024
    private const val BUFFER_BYTES = 32 * 1024

    fun write(file: File, source: InputStream, checkActive: () -> Unit = {}) {
        FileOutputStream(file).use { output ->
            ZipOutputStream(output.buffered(BUFFER_BYTES)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                zip.putNextEntry(ZipEntry(ENTRY_NAME).apply { time = 0L })
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    checkActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= FilterLimits.LIST_BYTES) { "List exceeds the 32 MB safety limit" }
                    zip.write(buffer, 0, count)
                }
                zip.closeEntry()
                zip.finish()
                zip.flush()
                output.fd.sync()
            }
        }
        check(file.length() <= MAX_ARCHIVE_BYTES) { "Compressed filter source is too large" }
    }

    /** ZipFile checks the central directory; our stream also checks the expanded size and CRC. */
    fun open(file: File): InputStream {
        if (file.length() > MAX_ARCHIVE_BYTES) throw FilterSourceIntegrityException("Compressed filter source is too large")
        val archive = try { ZipFile(file) } catch (error: Exception) {
            throw FilterSourceIntegrityException("Could not open filter source archive", error)
        }
        try {
            if (archive.size() != 1) throw FilterSourceIntegrityException("Expected one filter source in archive")
            val entry = archive.getEntry(ENTRY_NAME)
            if (entry == null || entry.isDirectory || entry.size !in 0..FilterLimits.LIST_BYTES.toLong() || entry.crc < 0) {
                throw FilterSourceIntegrityException("Invalid filter source archive entry")
            }
            return CheckedSourceStream(archive.getInputStream(entry), entry.size, entry.crc, archive)
        } catch (error: Throwable) {
            archive.close()
            throw error
        }
    }

    fun openPlain(file: File): InputStream {
        if (file.length() > FilterLimits.LIST_BYTES) throw FilterSourceIntegrityException("List exceeds the 32 MB safety limit")
        return CheckedSourceStream(file.inputStream(), null, null, null)
    }

    fun verify(file: File, checkActive: () -> Unit = {}) {
        open(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                checkActive()
                if (input.read(buffer) < 0) break
            }
        }
    }

    private class CheckedSourceStream(
        private val input: InputStream,
        private val expectedSize: Long?,
        private val expectedCrc: Long?,
        private val archive: ZipFile?
    ) : InputStream() {
        private var bytes = 0L
        private val crc = if (expectedCrc != null) CRC32() else null
        private var closed = false

        override fun read(): Int {
            val value = try { input.read() } catch (error: IOException) {
                throw FilterSourceIntegrityException("Could not read filter source", error)
            }
            if (value < 0) finish() else {
                addBytes(1)
                crc?.update(value)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = try { input.read(buffer, offset, length) } catch (error: IOException) {
                throw FilterSourceIntegrityException("Could not read filter source", error)
            }
            if (count < 0) finish() else {
                addBytes(count)
                crc?.update(buffer, offset, count)
            }
            return count
        }

        private fun addBytes(count: Int) {
            bytes += count
            if (bytes > FilterLimits.LIST_BYTES || expectedSize != null && bytes > expectedSize) {
                throw FilterSourceIntegrityException("Expanded filter source exceeds its size limit")
            }
        }

        private fun finish() {
            if (expectedSize != null && bytes != expectedSize || expectedCrc != null && crc?.value != expectedCrc) {
                throw FilterSourceIntegrityException("Filter source archive failed integrity verification")
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            try { input.close() } finally { archive?.close() }
        }
    }
}
