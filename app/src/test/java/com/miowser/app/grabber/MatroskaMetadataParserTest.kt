package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatroskaMetadataParserTest {
    @Test fun `reads duration using explicit timecode scale`() {
        val bytes = element(byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte()), uint(1_000_000)) +
            element(byteArrayOf(0x44, 0x89.toByte()), float64(125_500.0))
        assertEquals(125.5, MatroskaMetadataParser.durationSeconds(bytes)!!, 0.001)
    }

    @Test fun `uses matroska default timecode scale`() {
        val bytes = element(byteArrayOf(0x44, 0x89.toByte()), float32(90_000f))
        assertEquals(90.0, MatroskaMetadataParser.durationSeconds(bytes)!!, 0.001)
    }

    @Test fun `rejects absent and invalid duration`() {
        assertNull(MatroskaMetadataParser.durationSeconds(byteArrayOf(1, 2, 3)))
        assertNull(MatroskaMetadataParser.durationSeconds(
            element(byteArrayOf(0x44, 0x89.toByte()), float64(Double.NaN))))
    }

    private fun element(id: ByteArray, value: ByteArray) = id + byteArrayOf((0x80 or value.size).toByte()) + value
    private fun uint(value: Int) = byteArrayOf((value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    private fun float32(value: Float) = intBytes(value.toBits())
    private fun float64(value: Double): ByteArray {
        val bits = value.toBits()
        return ByteArray(8) { index -> (bits ushr (56 - index * 8)).toByte() }
    }
    private fun intBytes(value: Int) = ByteArray(4) { index -> (value ushr (24 - index * 8)).toByte() }
}
