package com.miowser.app.grabber

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoBmffMetadataParserTest {
    @Test
    fun `reads movie duration and largest track dimensions from a complete moov`() {
        val audio = track(width = 0, height = 0)
        val video = track(width = 1920, height = 1080)
        val bytes = box("moov", movieHeader(timescale = 1_000, duration = 125_500) + audio + video)

        val metadata = IsoBmffMetadataParser.parse(bytes)!!

        assertEquals(125.5, metadata.durationSeconds!!, 0.001)
        assertEquals("1920x1080", metadata.resolution)
    }

    @Test
    fun `finds moov after unrelated bytes in a bounded suffix`() {
        val prefix = ByteArray(37) { 0x55 }
        val bytes = prefix + box("moov", movieHeader(48_000, 96_000) + track(640, 360))

        val metadata = IsoBmffMetadataParser.parse(bytes)!!

        assertEquals(2.0, metadata.durationSeconds!!, 0.001)
        assertEquals("640x360", metadata.resolution)
    }

    @Test
    fun `reads version one 64 bit movie duration and track dimensions`() {
        val bytes = box("moov", movieHeaderV1(timescale = 1_000, duration = 7_200_000) + trackV1(3840, 2160))

        val metadata = IsoBmffMetadataParser.parse(bytes)!!

        assertEquals(7_200.0, metadata.durationSeconds!!, 0.001)
        assertEquals("3840x2160", metadata.resolution)
    }

    @Test
    fun `applies quarter turn track matrix to displayed resolution`() {
        val payload = ByteBuffer.allocate(84).order(ByteOrder.BIG_ENDIAN)
        payload.position(44) // matrix b
        payload.putInt(0x1_0000)
        payload.position(52) // matrix c
        payload.putInt(-0x1_0000)
        payload.position(76)
        payload.putInt(1920 shl 16)
        payload.putInt(1080 shl 16)
        val bytes = box("moov", movieHeader(1_000, 1_000) + box("trak", box("tkhd", payload.array())))

        assertEquals("1080x1920", IsoBmffMetadataParser.parse(bytes)?.resolution)
    }

    @Test
    fun `rejects incomplete and overflowing boxes`() {
        assertNull(IsoBmffMetadataParser.parse(box("moov", movieHeader(1_000, 5_000)).copyOf(20)))
        val invalid = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .putInt(1).put("moov".toByteArray()).putLong(-1).array()
        assertNull(IsoBmffMetadataParser.parse(invalid))
    }

    private fun movieHeader(timescale: Int, duration: Int): ByteArray {
        val payload = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN)
        payload.position(12)
        payload.putInt(timescale)
        payload.putInt(duration)
        return box("mvhd", payload.array())
    }

    private fun track(width: Int, height: Int): ByteArray {
        val payload = ByteBuffer.allocate(84).order(ByteOrder.BIG_ENDIAN)
        payload.position(76)
        payload.putInt(width shl 16)
        payload.putInt(height shl 16)
        return box("trak", box("tkhd", payload.array()))
    }

    private fun movieHeaderV1(timescale: Int, duration: Long): ByteArray {
        val payload = ByteBuffer.allocate(120).order(ByteOrder.BIG_ENDIAN)
        payload.put(0, 1)
        payload.position(20)
        payload.putInt(timescale)
        payload.putLong(duration)
        return box("mvhd", payload.array())
    }

    private fun trackV1(width: Int, height: Int): ByteArray {
        val payload = ByteBuffer.allocate(96).order(ByteOrder.BIG_ENDIAN)
        payload.put(0, 1)
        payload.position(88)
        payload.putInt(width shl 16)
        payload.putInt(height shl 16)
        return box("trak", box("tkhd", payload.array()))
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(8 + payload.size)
            .put(type.toByteArray(Charsets.ISO_8859_1))
            .put(payload)
            .array()
}
