package com.miowser.app.adblock

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class FilterListFilesTest {
    @Test fun `bundled source compresses and streams identical parser results`() = withDirectory { _, files ->
        val original = File("src/main/assets/easylist.txt")
        val expected = original.bufferedReader().use { FilterListParser.scan(it, FilterSource.SUBSCRIBED) }
        files.install("easylist", { original.inputStream().buffered() })
        val compressed = files.existing("easylist")!!
        assertTrue(compressed.length() < original.length())
        assertTrue(files.contentEquals("easylist", original))
        val actual = FilterListFiles.reader(compressed).use { FilterListParser.scan(it, FilterSource.SUBSCRIBED) }
        assertEquals(expected, actual)
        println("Bundled EasyList source: ${original.length()} plaintext bytes -> ${compressed.length()} ZIP bytes; parser counts unchanged (not a device timing)")
    }

    @Test fun `legacy text remains readable without eager migration`() = withDirectory { directory, files ->
        val legacy = File(directory, "list-test.txt").apply { writeText(RULES); setLastModified(123_000L) }
        assertEquals(legacy, files.existing("test"))
        assertEquals(RULES, FilterListFiles.reader(legacy).use { it.readText() })
        assertEquals(123_000L, legacy.lastModified())
        assertFalse(File(directory, "list-test.zip").exists())
    }

    @Test fun `successful migration preserves source bytes in a smaller ZIP`() = withDirectory { directory, files ->
        val legacy = File(directory, "list-test.txt").apply { writeText(RULES.repeat(4_000)) }
        val originalBytes = legacy.length()
        // Keep a file handle to the old source open until the replacement is verified.
        files.install("test", { legacy.inputStream() })
        val zip = files.existing("test")!!
        assertEquals("zip", zip.extension)
        assertTrue("Expected compression of repeated filter text", zip.length() < originalBytes / 4)
        assertFalse(legacy.exists())
        FilterListFiles.reader(zip).use { reader ->
            repeat(4_000) { RULES.lineSequence().filter { it.isNotEmpty() }.forEach { line -> assertEquals(line, reader.readLine()) } }
            assertNull(reader.readLine())
        }
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun `content equality compares expanded bytes and rejects different tails`() = withDirectory { directory, files ->
        val candidate = File(directory, "candidate.download").apply { writeText(RULES.repeat(1_000)) }
        files.install("test", { candidate.inputStream() })
        val zip = files.existing("test")!!
        val timestamp = zip.lastModified()
        assertNotEquals(candidate.length(), zip.length())
        assertTrue(files.contentEquals("test", candidate))
        assertEquals(timestamp, zip.lastModified())
        candidate.appendText("!")
        assertFalse(files.contentEquals("test", candidate))
        candidate.writeText(RULES.repeat(1_000).dropLast(1))
        assertFalse(files.contentEquals("test", candidate))
        candidate.writeText(RULES.repeat(1_000).replace(".advert", ".otherx"))
        assertFalse(files.contentEquals("test", candidate))
    }

    @Test fun `failed replacement retains ZIP and cleans staged output`() = withDirectory { directory, files ->
        files.install("test", { RULES.byteInputStream() })
        val before = files.existing("test")!!.readBytes()
        assertThrows(IOException::class.java) {
            files.install("test", { object : InputStream() {
                var reads = 0
                override fun read(): Int = error("bulk reads expected")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (reads++ > 0) throw IOException("simulated download/disk failure")
                    buffer[offset] = '!'.code.toByte()
                    return 1
                }
            } })
        }
        assertArrayEquals(before, files.existing("test")!!.readBytes())
        assertEquals(RULES, FilterListFiles.reader(files.existing("test")!!).use { it.readText() })
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun `canceled migration retains legacy source even after ZIP writing`() = withDirectory { directory, files ->
        val legacy = File(directory, "list-test.txt").apply { writeText(RULES) }
        var checks = 0
        assertThrows(CancellationException::class.java) {
            files.install("test", { legacy.inputStream() }) {
                if (++checks == 3) throw CancellationException("cancel during verification")
            }
        }
        assertEquals(legacy, files.existing("test"))
        assertEquals(RULES, legacy.readText())
        assertTrue(directory.listFiles()!!.none { it.extension in setOf("tmp", "zip") })
    }

    @Test fun `oversized streamed source cannot replace installed protection`() = withDirectory { directory, files ->
        files.install("test", { RULES.byteInputStream() })
        var largestRead = 0
        assertThrows(IllegalStateException::class.java) {
            files.install("test", { object : InputStream() {
                var remaining = FilterLimits.LIST_BYTES + 1
                override fun read(): Int = error("bulk reads expected")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    largestRead = maxOf(largestRead, length)
                    if (remaining == 0) return -1
                    val count = minOf(length, remaining)
                    buffer.fill('a'.code.toByte(), offset, offset + count)
                    remaining -= count
                    return count
                }
            } })
        }
        assertTrue(largestRead in 1..32 * 1024)
        assertEquals(RULES, FilterListFiles.reader(files.existing("test")!!).use { it.readText() })
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    @Test fun `truncation wrong entries and CRC damage are rejected`() = withDirectory { directory, files ->
        files.install("test", { RULES.byteInputStream() })
        val valid = files.existing("test")!!.readBytes()
        val bad = File(directory, "bad.zip")
        bad.writeBytes(valid.copyOf(valid.size - 24))
        assertThrows(FilterSourceIntegrityException::class.java) { FilterSourceArchive.verify(bad) }
        listOf(listOf("../filters.txt"), listOf("filters.txt/"), listOf("filters.txt", "extra.txt")).forEach { names ->
            zip(bad, names)
            assertThrows(FilterSourceIntegrityException::class.java) { FilterSourceArchive.verify(bad) }
        }
        zip(bad, listOf("filters.txt"), stored = true)
        val bytes = bad.readBytes()
        val payload = "unique-stored-payload".toByteArray()
        val position = (0..bytes.size - payload.size).first { start -> payload.indices.all { bytes[start + it] == payload[it] } }
        bytes[position] = 'X'.code.toByte()
        bad.writeBytes(bytes)
        assertThrows(FilterSourceIntegrityException::class.java) { FilterSourceArchive.verify(bad) }
    }

    @Test fun `expanded size is enforced even when ZIP metadata claims a smaller entry`() = withDirectory { directory, files ->
        files.install("test", { RULES.repeat(100).byteInputStream() })
        val bad = File(directory, "bad.zip")
        val bytes = files.existing("test")!!.readBytes()
        // Central directory uncompressed-size field. Make a valid deflate payload disagree
        // with its declared size to exercise the streaming check rather than just a size precheck.
        val position = (0..bytes.size - 4).first { start -> bytes[start] == 0x50.toByte() && bytes[start + 1] == 0x4b.toByte() && bytes[start + 2] == 1.toByte() && bytes[start + 3] == 2.toByte() }
        repeat(4) { bytes[position + 24 + it] = 0 }
        bad.writeBytes(bytes)
        assertThrows(FilterSourceIntegrityException::class.java) { FilterSourceArchive.verify(bad) }
    }

    @Test fun `changed source has a new fingerprint timestamp and ZIP takes precedence`() = withDirectory { directory, files ->
        files.install("test", { RULES.byteInputStream() })
        val first = files.existing("test")!!
        val future = System.currentTimeMillis() + 60_000
        assertTrue(first.setLastModified(future))
        File(directory, "list-test.txt").writeText("obsolete")
        assertEquals(first, files.existing("test"))
        files.install("test", { "||new.example^\n".byteInputStream() })
        assertTrue(files.existing("test")!!.lastModified() > future)
        assertEquals("||new.example^\n", FilterListFiles.reader(files.existing("test")!!).use { it.readText() })
        assertFalse(File(directory, "list-test.txt").exists())
    }

    @Test fun `compressed source preserves parsed rules cosmetics and compiled decisions`() = withDirectory { _, files ->
        val expected = FilterListParser.parse(RULES, FilterSource.SUBSCRIBED)
        files.install("test", { RULES.byteInputStream() })
        val actual = FilterListFiles.reader(files.existing("test")!!).use { FilterListParser.parse(it, FilterSource.SUBSCRIBED) }
        assertEquals(expected, actual)
        val engine = FilterEngine().apply { actual.networkRules.forEach(::addRule); optimizeTokenIndexes() }
        val bytes = java.io.ByteArrayOutputStream().also { output ->
            java.io.DataOutputStream(output).use { CompiledFilterSnapshot.write(it, "zip", engine, emptyMap(), actual.cosmeticRules, 0) }
        }.toByteArray()
        val restored = java.io.DataInputStream(bytes.inputStream()).use { CompiledFilterSnapshot.read(it, "zip")!!.network }
        listOf(engine, restored).forEach { matcher ->
            assertTrue(matcher.classify("https://ads.example/ad.js", "ads.example", "site.example", ResourceType.SCRIPT) is FilterDecision.Block)
            assertEquals(FilterDecision.Allow, matcher.classify("https://ads.example/allowed.js", "ads.example", "site.example", ResourceType.SCRIPT))
            assertTrue(matcher.classify("https://cdn.example/track-one", "cdn.example", "site.example", ResourceType.SCRIPT) is FilterDecision.Block)
        }
    }

    @Test fun `removal handles both formats and preserves unrelated private data`() = withDirectory { directory, files ->
        files.install("test", { RULES.byteInputStream() })
        File(directory, "list-test.txt").writeText(RULES)
        val manual = File(directory, "manual-rules.txt").apply { writeText("custom") }
        val other = File(directory, "list-other.txt").apply { writeText("other") }
        files.delete("test")
        assertNull(files.existing("test"))
        assertEquals("custom", manual.readText())
        assertEquals("other", other.readText())
    }

    private fun zip(file: File, names: List<String>, stored: Boolean = false) {
        val bytes = "unique-stored-payload".toByteArray()
        ZipOutputStream(file.outputStream()).use { output ->
            names.forEach { name ->
                val entry = ZipEntry(name)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                }
                output.putNextEntry(entry)
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun withDirectory(action: (File, FilterListFiles) -> Unit) {
        val directory = Files.createTempDirectory("miowser-filter-sources").toFile()
        try { action(directory, FilterListFiles(directory)) } finally { directory.deleteRecursively() }
    }

    private companion object {
        val RULES = "! café source\n0.0.0.0 hosts.example\n||ads.example^\n@@||ads.example/allowed.js\$script\ntrack*one\$third-party,script\nsite.example,~safe.site.example##.advert\n##.sponsor[data-title='é']\n"
    }
}
