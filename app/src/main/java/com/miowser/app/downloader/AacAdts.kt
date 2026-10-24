package com.miowser.app.downloader

import java.nio.ByteBuffer

/** Removes ADTS transport headers before placing AAC access units in an MP4 track. */
internal object AacAdts {
    private val sampleRates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000,
        24000, 22050, 16000, 12000, 11025, 8000, 7350)

    data class Frame(
        val payloadOffset: Int,
        val payloadSize: Int,
        val durationUs: Long,
        val audioSpecificConfig: ByteArray
    )

    /** Null means this sample is already raw AAC. Malformed ADTS is rejected, not saved as MP4. */
    fun frames(buffer: ByteBuffer, size: Int): List<Frame>? {
        if (size < 7 || (buffer.get(0).toInt() and 0xff) != 0xff ||
            (buffer.get(1).toInt() and 0xf6) != 0xf0
        ) return null
        val result = mutableListOf<Frame>()
        var offset = 0
        while (offset < size) {
            require(size - offset >= 7) { "Truncated AAC ADTS header" }
            val b0 = buffer.get(offset).toInt() and 0xff
            val b1 = buffer.get(offset + 1).toInt() and 0xff
            val b2 = buffer.get(offset + 2).toInt() and 0xff
            val b3 = buffer.get(offset + 3).toInt() and 0xff
            val b4 = buffer.get(offset + 4).toInt() and 0xff
            val b5 = buffer.get(offset + 5).toInt() and 0xff
            val b6 = buffer.get(offset + 6).toInt() and 0xff
            require(b0 == 0xff && (b1 and 0xf6) == 0xf0) { "Invalid AAC ADTS frame" }
            val frequencyIndex = (b2 and 0x3c) shr 2
            require(frequencyIndex in sampleRates.indices) { "Unsupported AAC sample rate" }
            val channelConfig = ((b2 and 1) shl 2) or ((b3 and 0xc0) shr 6)
            val objectType = ((b2 and 0xc0) shr 6) + 1
            val headerSize = if ((b1 and 1) != 0) 7 else 9
            val frameSize = ((b3 and 3) shl 11) or (b4 shl 3) or ((b5 and 0xe0) shr 5)
            require(frameSize > headerSize && frameSize <= size - offset) { "Invalid AAC ADTS frame length" }
            val config = byteArrayOf(
                ((objectType shl 3) or (frequencyIndex shr 1)).toByte(),
                (((frequencyIndex and 1) shl 7) or (channelConfig shl 3)).toByte()
            )
            result += Frame(
                payloadOffset = offset + headerSize,
                payloadSize = frameSize - headerSize,
                durationUs = 1_024_000_000L * ((b6 and 3) + 1) / sampleRates[frequencyIndex],
                audioSpecificConfig = config
            )
            offset += frameSize
        }
        return result
    }
}
