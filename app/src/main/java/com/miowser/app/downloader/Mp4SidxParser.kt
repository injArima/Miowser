package com.miowser.app.downloader

import com.miowser.app.grabber.DashByteRange

data class SidxReference(
    val byteRange: DashByteRange,
    val durationUnits: Long,
    val startsWithSap: Boolean,
    val sapType: Int,
    val sapDeltaTime: Long
)

data class SidxIndex(
    val version: Int,
    val referenceId: Long,
    val timescale: Long,
    val earliestPresentationTime: Long,
    val firstOffset: Long,
    val references: List<SidxReference>
)

sealed interface SidxParseResult {
    data class Success(val index: SidxIndex) : SidxParseResult
    data class Failure(val message: String) : SidxParseResult
}

/** Strict ISO-BMFF `sidx` parser for a bounded HTTP index-range response. */
object Mp4SidxParser {
    fun parse(bytes: ByteArray, absoluteRangeStart: Long): SidxParseResult {
        if (absoluteRangeStart < 0L) return SidxParseResult.Failure("Invalid sidx range start")
        var boxOffset = 0
        while (boxOffset <= bytes.size - 8) {
            val size32 = readUInt32(bytes, boxOffset) ?: return malformedBox()
            val type = String(bytes, boxOffset + 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            val boxSize = when (size32) {
                0L -> (bytes.size - boxOffset).toLong()
                1L -> {
                    if (boxOffset > bytes.size - 16) return malformedBox()
                    headerSize = 16
                    readUInt64(bytes, boxOffset + 8) ?: return SidxParseResult.Failure("sidx box size is too large")
                }
                else -> size32
            }
            val remaining = (bytes.size - boxOffset).toLong()
            if (boxSize < headerSize.toLong() || boxSize > remaining || boxSize > Int.MAX_VALUE.toLong()) return malformedBox()
            if (type == "sidx") {
                return parseSidxBox(bytes, boxOffset, boxSize.toInt(), headerSize, absoluteRangeStart)
            }
            boxOffset += boxSize.toInt()
        }
        return SidxParseResult.Failure("Index range does not contain an sidx box")
    }

    private fun parseSidxBox(
        bytes: ByteArray,
        boxOffset: Int,
        boxSize: Int,
        headerSize: Int,
        absoluteRangeStart: Long
    ): SidxParseResult {
        val boxEnd = boxOffset + boxSize
        var cursor = boxOffset + headerSize
        if (cursor > boxEnd - 12) return malformedSidx()

        val version = bytes[cursor].toInt() and 0xff
        if (version !in 0..1) return SidxParseResult.Failure("Unsupported sidx version $version")
        cursor += 4 // version + flags
        val referenceId = readUInt32(bytes, cursor) ?: return malformedSidx()
        cursor += 4
        val timescale = readUInt32(bytes, cursor)?.takeIf { it > 0L }
            ?: return SidxParseResult.Failure("sidx timescale is zero or missing")
        cursor += 4

        val earliestPresentationTime: Long
        val firstOffset: Long
        if (version == 0) {
            earliestPresentationTime = readUInt32(bytes, cursor) ?: return malformedSidx()
            firstOffset = readUInt32(bytes, cursor + 4) ?: return malformedSidx()
            cursor += 8
        } else {
            earliestPresentationTime = readUInt64(bytes, cursor)
                ?: return SidxParseResult.Failure("sidx earliest presentation time is too large")
            firstOffset = readUInt64(bytes, cursor + 8)
                ?: return SidxParseResult.Failure("sidx first offset is too large")
            cursor += 16
        }
        if (cursor > boxEnd - 4) return malformedSidx()
        cursor += 2 // reserved
        val referenceCount = readUInt16(bytes, cursor) ?: return malformedSidx()
        cursor += 2
        if (referenceCount == 0) return SidxParseResult.Failure("sidx contains no media references")
        if (referenceCount.toLong() * 12L > boxEnd - cursor) return malformedSidx()

        val absoluteBoxEnd = safeAdd(absoluteRangeStart, boxOffset.toLong())
            ?.let { safeAdd(it, boxSize.toLong()) }
            ?: return SidxParseResult.Failure("sidx absolute offset overflow")
        var mediaOffset = safeAdd(absoluteBoxEnd, firstOffset)
            ?: return SidxParseResult.Failure("sidx first media offset overflow")
        val references = ArrayList<SidxReference>(referenceCount)
        repeat(referenceCount) {
            val referenceWord = readUInt32(bytes, cursor) ?: return malformedSidx()
            val referenceType = (referenceWord ushr 31) and 1L
            val referencedSize = referenceWord and 0x7fff_ffffL
            val duration = readUInt32(bytes, cursor + 4) ?: return malformedSidx()
            val sapWord = readUInt32(bytes, cursor + 8) ?: return malformedSidx()
            cursor += 12
            if (referenceType != 0L) {
                return SidxParseResult.Failure("Nested sidx references are not supported")
            }
            if (referencedSize <= 0L) return SidxParseResult.Failure("sidx contains an empty media reference")
            val end = safeAdd(mediaOffset, referencedSize - 1L)
                ?: return SidxParseResult.Failure("sidx media range overflow")
            references += SidxReference(
                byteRange = DashByteRange(mediaOffset, end),
                durationUnits = duration,
                startsWithSap = (sapWord ushr 31) != 0L,
                sapType = ((sapWord ushr 28) and 0x7L).toInt(),
                sapDeltaTime = sapWord and 0x0fff_ffffL
            )
            mediaOffset = safeAdd(end, 1L)
                ?: return SidxParseResult.Failure("sidx media range overflow")
        }
        return SidxParseResult.Success(
            SidxIndex(version, referenceId, timescale, earliestPresentationTime, firstOffset, references)
        )
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset > bytes.size - 2) return null
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset > bytes.size - 4) return null
        return ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
    }

    /** Returns null when the unsigned value cannot fit in Kotlin's signed Long. */
    private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
        val high = readUInt32(bytes, offset) ?: return null
        val low = readUInt32(bytes, offset + 4) ?: return null
        if (high > 0x7fff_ffffL) return null
        return (high shl 32) or low
    }

    private fun safeAdd(first: Long, second: Long): Long? =
        runCatching { Math.addExact(first, second) }.getOrNull()

    private fun malformedBox() = SidxParseResult.Failure("Malformed MP4 box in index range")
    private fun malformedSidx() = SidxParseResult.Failure("Malformed or truncated sidx box")
}
