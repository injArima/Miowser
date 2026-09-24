package com.miowser.app.downloader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4SidxParserTest {
    @Test
    fun `parses version zero references into absolute byte ranges`() {
        val bytes = sidx(version = 0, earliest = 25, firstOffset = 10, references = listOf(100 to 50, 200 to 75))
        val result = Mp4SidxParser.parse(bytes, absoluteRangeStart = 1_000) as SidxParseResult.Success

        assertEquals(0, result.index.version)
        assertEquals(1_000L, result.index.timescale)
        assertEquals(25L, result.index.earliestPresentationTime)
        val firstMediaByte = 1_000L + bytes.size + 10L
        assertEquals(firstMediaByte, result.index.references[0].byteRange.start)
        assertEquals(firstMediaByte + 99L, result.index.references[0].byteRange.endInclusive)
        assertEquals(firstMediaByte + 100L, result.index.references[1].byteRange.start)
        assertEquals(75L, result.index.references[1].durationUnits)
    }

    @Test
    fun `parses version one 64 bit timing and offset fields`() {
        val firstOffset = 0x1_0000_0005L
        val result = Mp4SidxParser.parse(
            sidx(version = 1, earliest = 0x1_0000_0001L, firstOffset = firstOffset, references = listOf(32 to 10)),
            absoluteRangeStart = 50
        ) as SidxParseResult.Success

        assertEquals(0x1_0000_0001L, result.index.earliestPresentationTime)
        assertEquals(firstOffset, result.index.firstOffset)
    }

    @Test
    fun `rejects nested references unsupported versions and truncated boxes`() {
        assertTrue(Mp4SidxParser.parse(sidx(0, 0, 0, listOf(10 to 1), nested = true), 0) is SidxParseResult.Failure)
        val unsupported = sidx(0, 0, 0, listOf(10 to 1)).also { it[8] = 2 }
        assertTrue(Mp4SidxParser.parse(unsupported, 0) is SidxParseResult.Failure)
        assertTrue(Mp4SidxParser.parse(sidx(0, 0, 0, listOf(10 to 1)).copyOf(20), 0) is SidxParseResult.Failure)
    }

    @Test
    fun `parses strict HTTP content ranges`() {
        assertEquals(100L to 199L, parseDashContentRange("bytes 100-199/1000"))
        assertEquals(0L to 9L, parseDashContentRange("BYTES 0-9/*"))
        assertEquals(null, parseDashContentRange("bytes 200-100/1000"))
        assertEquals(null, parseDashContentRange("not-a-range"))
    }

    private fun sidx(
        version: Int,
        earliest: Long,
        firstOffset: Long,
        references: List<Pair<Int, Int>>,
        nested: Boolean = false
    ): ByteArray {
        val size = (if (version == 0) 32 else 40) + references.size * 12
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(size)
            put("sidx".toByteArray(Charsets.ISO_8859_1))
            put(version.toByte())
            put(byteArrayOf(0, 0, 0))
            putInt(7) // reference_ID
            putInt(1_000) // timescale
            if (version == 0) {
                putInt(earliest.toInt())
                putInt(firstOffset.toInt())
            } else {
                putLong(earliest)
                putLong(firstOffset)
            }
            putShort(0)
            putShort(references.size.toShort())
            references.forEachIndexed { index, (byteSize, duration) ->
                val typeBit = if (nested && index == 0) 0x8000_0000.toInt() else 0
                putInt(typeBit or byteSize)
                putInt(duration)
                putInt(0x9000_0000.toInt()) // starts_with_SAP=1, SAP_type=1
            }
        }.array()
    }
}
