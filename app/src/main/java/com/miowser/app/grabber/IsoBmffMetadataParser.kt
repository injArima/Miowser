package com.miowser.app.grabber

/** Metadata obtainable from a bounded MP4/ISO-BMFF prefix or suffix. */
data class IsoBmffMetadata(
    val durationSeconds: Double?,
    val width: Int?,
    val height: Int?
) {
    val resolution: String?
        get() = if (width != null && height != null && width > 0 && height > 0) "${width}x$height" else null
}

/**
 * Bounds-checked parser for the two pieces the grabber needs from an MP4 `moov` box. It accepts a
 * bounded prefix or suffix and scans for a complete `moov`; it does not request or decode media
 * samples. Unknown, fragmented, or out-of-window metadata stays unknown instead of being guessed.
 */
object IsoBmffMetadataParser {
    fun parse(bytes: ByteArray): IsoBmffMetadata? {
        val moov = findCompleteBox(bytes, "moov") ?: return null
        var duration: Double? = null
        var bestWidth: Int? = null
        var bestHeight: Int? = null

        forEachChild(bytes, moov) { child ->
            when (child.type) {
                "mvhd" -> duration = parseMovieDuration(bytes, child) ?: duration
                "trak" -> parseTrackDimensions(bytes, child)?.let { (width, height) ->
                    val oldArea = (bestWidth?.toLong() ?: 0L) * (bestHeight?.toLong() ?: 0L)
                    val newArea = width.toLong() * height.toLong()
                    if (newArea > oldArea) {
                        bestWidth = width
                        bestHeight = height
                    }
                }
            }
        }
        if (duration == null && bestWidth == null) return null
        return IsoBmffMetadata(duration, bestWidth, bestHeight)
    }

    private fun parseMovieDuration(bytes: ByteArray, box: Box): Double? {
        val start = box.contentStart
        if (start >= box.endExclusive) return null
        val version = bytes[start].toInt() and 0xff
        val timescaleOffset = start + if (version == 1) 20 else if (version == 0) 12 else return null
        val durationOffset = start + if (version == 1) 24 else 16
        val timescale = readUInt32(bytes, timescaleOffset, box.endExclusive)?.takeIf { it > 0L } ?: return null
        val durationUnits = if (version == 1) {
            readUInt64(bytes, durationOffset, box.endExclusive)
        } else {
            readUInt32(bytes, durationOffset, box.endExclusive)
        } ?: return null
        // All-one-bits is ISO-BMFF's unknown-duration sentinel.
        if ((version == 0 && durationUnits == 0xffff_ffffL) || durationUnits == Long.MAX_VALUE) return null
        return (durationUnits.toDouble() / timescale.toDouble()).takeIf { it.isFinite() && it > 0.0 }
    }

    private fun parseTrackDimensions(bytes: ByteArray, track: Box): Pair<Int, Int>? {
        var dimensions: Pair<Int, Int>? = null
        forEachChild(bytes, track) { child ->
            if (child.type != "tkhd") return@forEachChild
            val start = child.contentStart
            if (start >= child.endExclusive) return@forEachChild
            val version = bytes[start].toInt() and 0xff
            val widthOffset = start + if (version == 1) 88 else if (version == 0) 76 else return@forEachChild
            val heightOffset = widthOffset + 4
            val widthFixed = readUInt32(bytes, widthOffset, child.endExclusive) ?: return@forEachChild
            val heightFixed = readUInt32(bytes, heightOffset, child.endExclusive) ?: return@forEachChild
            var width = ((widthFixed + 0x8000L) ushr 16).toInt()
            var height = ((heightFixed + 0x8000L) ushr 16).toInt()
            val matrixOffset = start + if (version == 1) 52 else 40
            val matrixB = readUInt32(bytes, matrixOffset + 4, child.endExclusive)?.toInt()
            val matrixC = readUInt32(bytes, matrixOffset + 12, child.endExclusive)?.toInt()
            // A quarter-turn presentation matrix means the encoded width/height are transposed.
            if (matrixB != null && matrixC != null &&
                kotlin.math.abs(matrixB.toLong()) == 0x1_0000L &&
                kotlin.math.abs(matrixC.toLong()) == 0x1_0000L
            ) {
                val oldWidth = width
                width = height
                height = oldWidth
            }
            if (width > 0 && height > 0) dimensions = width to height
        }
        return dimensions
    }

    private fun findCompleteBox(bytes: ByteArray, type: String): Box? {
        if (bytes.size < 8) return null
        val expected = type.toByteArray(Charsets.ISO_8859_1)
        for (typeOffset in 4..bytes.size - 4) {
            if (!expected.indices.all { bytes[typeOffset + it] == expected[it] }) continue
            val start = typeOffset - 4
            readBox(bytes, start, bytes.size)?.takeIf { it.type == type }?.let { return it }
        }
        return null
    }

    private inline fun forEachChild(bytes: ByteArray, parent: Box, action: (Box) -> Unit) {
        var offset = parent.contentStart
        while (offset <= parent.endExclusive - 8) {
            val child = readBox(bytes, offset, parent.endExclusive) ?: break
            action(child)
            if (child.endExclusive <= offset) break
            offset = child.endExclusive
        }
    }

    private fun readBox(bytes: ByteArray, start: Int, limit: Int): Box? {
        if (start < 0 || start > limit - 8) return null
        val size32 = readUInt32(bytes, start, limit) ?: return null
        val type = String(bytes, start + 4, 4, Charsets.ISO_8859_1)
        val headerSize: Int
        val size: Long
        when (size32) {
            0L -> {
                headerSize = 8
                size = (limit - start).toLong()
            }
            1L -> {
                headerSize = 16
                size = readUInt64(bytes, start + 8, limit) ?: return null
            }
            else -> {
                headerSize = 8
                size = size32
            }
        }
        if (size < headerSize || size > Int.MAX_VALUE || size > (limit - start).toLong()) return null
        return Box(type, start + headerSize, start + size.toInt())
    }

    private fun readUInt32(bytes: ByteArray, offset: Int, limit: Int): Long? {
        if (offset < 0 || offset > limit - 4 || limit > bytes.size) return null
        return ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int, limit: Int): Long? {
        if (offset < 0 || offset > limit - 8 || limit > bytes.size) return null
        var value = 0L
        repeat(8) { index ->
            val next = bytes[offset + index].toLong() and 0xffL
            if (value > (Long.MAX_VALUE - next) ushr 8) return null
            value = (value shl 8) or next
        }
        return value
    }

    private data class Box(val type: String, val contentStart: Int, val endExclusive: Int)
}
