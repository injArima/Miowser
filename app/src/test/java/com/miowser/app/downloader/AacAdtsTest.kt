package com.miowser.app.downloader

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AacAdtsTest {
    private val frame = byteArrayOf(
        0xff.toByte(), 0xf1.toByte(), 0x50, 0x80.toByte(), 0x01, 0x60, 0x00,
        1, 2, 3, 4
    )

    @Test fun `extracts raw AAC frames and MP4 audio configuration`() {
        val bytes = frame + frame
        val frames = AacAdts.frames(ByteBuffer.wrap(bytes), bytes.size)!!
        assertEquals(2, frames.size)
        assertEquals(7, frames[0].payloadOffset)
        assertEquals(4, frames[0].payloadSize)
        assertEquals(18, frames[1].payloadOffset)
        assertEquals(23_219L, frames[0].durationUs)
        assertArrayEquals(byteArrayOf(0x12, 0x10), frames[0].audioSpecificConfig)
    }

    @Test fun `already raw AAC is left alone`() {
        assertNull(AacAdts.frames(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)), 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated ADTS fails instead of producing a broken MP4`() {
        AacAdts.frames(ByteBuffer.wrap(frame.copyOf(9)), 9)
    }
}
