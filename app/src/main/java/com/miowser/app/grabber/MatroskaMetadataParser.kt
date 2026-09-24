package com.miowser.app.grabber

/** Extracts Duration and TimecodeScale from a bounded WebM/Matroska prefix. */
object MatroskaMetadataParser {
    private val TIMECODE_SCALE = byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte())
    private val DURATION = byteArrayOf(0x44, 0x89.toByte())

    fun durationSeconds(bytes: ByteArray): Double? {
        val scale = findElement(bytes, TIMECODE_SCALE)?.let(::readUnsigned) ?: 1_000_000L
        val durationBytes = findElement(bytes, DURATION) ?: return null
        val duration = when (durationBytes.size) {
            4 -> Float.fromBits(readUnsigned(durationBytes).toInt()).toDouble()
            8 -> Double.fromBits(readUnsigned(durationBytes))
            else -> return null
        }
        return (duration * scale.toDouble() / 1_000_000_000.0)
            .takeIf { it.isFinite() && it > 0.0 }
    }

    private fun findElement(bytes: ByteArray, id: ByteArray): ByteArray? {
        for (offset in 0..bytes.size - id.size - 1) {
            if (!id.indices.all { bytes[offset + it] == id[it] }) continue
            val sizeOffset = offset + id.size
            val first = bytes[sizeOffset].toInt() and 0xff
            val length = (1..8).firstOrNull { first and (0x80 ushr (it - 1)) != 0 } ?: continue
            if (sizeOffset + length > bytes.size) continue
            var size = (first and (0xff ushr length)).toLong()
            for (index in 1 until length) size = (size shl 8) or (bytes[sizeOffset + index].toLong() and 0xff)
            if (size <= 0 || size > 8 || sizeOffset + length + size > bytes.size) continue
            return bytes.copyOfRange(sizeOffset + length, sizeOffset + length + size.toInt())
        }
        return null
    }

    private fun readUnsigned(bytes: ByteArray): Long {
        var value = 0L
        for (byte in bytes) value = (value shl 8) or (byte.toLong() and 0xff)
        return value
    }
}
