package com.miowser.app.grabber

/**
 * One real media segment, ready to download: its absolute URL, its own #EXTINF duration, its
 * media-sequence number (needed to derive an AES-128 IV when the playlist doesn't supply one
 * explicitly — RFC 8216 §5.2), and which key (if any) decrypts it.
 */
data class HlsSegment(
    val url: String,
    val durationSeconds: Double,
    val sequenceNumber: Long,
    val keyUri: String?, // null = not encrypted
    val ivHex: String?, // explicit "IV=0x..." from the #EXT-X-KEY tag; null = derive from sequenceNumber
    val byteRange: HlsByteRange? = null,
    val initialization: HlsInitialization? = null
)

data class HlsByteRange(val start: Long, val length: Long) {
    val endInclusive: Long get() = start + length - 1L
}

data class HlsInitialization(
    val url: String,
    val byteRange: HlsByteRange?,
    val keyUri: String?,
    val ivHex: String?
)

data class HlsLeafPlaylist(
    val segments: List<HlsSegment>,
    val totalDurationSeconds: Double,
    val unsupportedReason: String? = null
)

/**
 * Full segment enumeration for a leaf/media HLS playlist — everything the downloader needs to
 * actually fetch and decrypt every segment, as opposed to ManifestSniffer's lightweight
 * detection-time sniff (confirms type, counts variants, samples only the first segment). Kept as
 * a separate parser so the cheap classification path every candidate URL goes through doesn't
 * pay for full segment-list parsing it will almost never need.
 *
 * Tracks the *current* encryption key as a running state while scanning line by line, so a
 * playlist with more than one #EXT-X-KEY (a mid-playlist key rotation) attaches the right key to
 * each segment rather than assuming one key for the whole file.
 */
object HlsPlaylistParser {
    private val EXTINF = Regex("#EXTINF:([\\d.]+)")
    private val KEY_METHOD = Regex("METHOD=([A-Za-z0-9-]+)")
    private val KEY_URI = Regex("URI=\"([^\"]+)\"")
    private val KEY_IV = Regex("IV=0[xX]([0-9A-Fa-f]+)")
    private val MEDIA_SEQUENCE = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)")
    private val MAP_URI = Regex("(?:^|[:,])URI=\"([^\"]+)\"")
    private val MAP_RANGE = Regex("(?:^|[:,])BYTERANGE=\"([^\"]+)\"")

    private fun parseRange(value: String, previousEnd: Long?): HlsByteRange? {
        val parts = value.trim().split('@', limit = 2)
        val length = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val start = if (parts.size == 2) parts[1].toLongOrNull() else previousEnd?.plus(1)
        if (start == null || start < 0 || start > Long.MAX_VALUE - length) return null
        return HlsByteRange(start, length)
    }

    fun parseLeaf(body: String, manifestUrl: String): HlsLeafPlaylist {
        var sequenceNumber = MEDIA_SEQUENCE.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        var currentKeyUri: String? = null
        var currentIvHex: String? = null
        var currentMap: HlsInitialization? = null
        var pendingRange: String? = null
        var previousSegmentRangeUrl: String? = null
        var previousSegmentRangeEnd: Long? = null
        var previousMapRangeUrl: String? = null
        var previousMapRangeEnd: Long? = null
        var unsupportedReason: String? = null
        var pendingDuration: Double? = null

        val segments = mutableListOf<HlsSegment>()
        var total = 0.0

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val method = KEY_METHOD.find(line)?.groupValues?.get(1)
                    if (method != null && !method.equals("NONE", true) && !method.equals("AES-128", true)) {
                        unsupportedReason = "HLS encryption method $method is not supported"
                    }
                    currentKeyUri = if (method == null || method.equals("NONE", ignoreCase = true)) {
                        null
                    } else {
                        KEY_URI.find(line)?.groupValues?.get(1)?.let { ManifestSniffer.resolveUrl(manifestUrl, it) }
                    }
                    currentIvHex = KEY_IV.find(line)?.groupValues?.get(1)
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val mapUrl = MAP_URI.find(line)?.groupValues?.get(1)
                        ?.let { ManifestSniffer.resolveUrl(manifestUrl, it) }
                    if (mapUrl == null) {
                        unsupportedReason = "HLS initialization segment has no URI"
                    } else {
                        val rangeText = MAP_RANGE.find(line)?.groupValues?.get(1)
                        val range = rangeText?.let {
                            parseRange(it, previousMapRangeEnd?.takeIf { previousMapRangeUrl == mapUrl })
                        }
                        if (rangeText != null && range == null) unsupportedReason = "Invalid HLS initialization byte range"
                        if (currentKeyUri != null && currentIvHex == null) {
                            unsupportedReason = "Encrypted HLS initialization segment needs an explicit IV"
                        }
                        currentMap = HlsInitialization(mapUrl, range, currentKeyUri, currentIvHex)
                        previousMapRangeUrl = mapUrl
                        previousMapRangeEnd = range?.endInclusive
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    pendingRange = line.substringAfter(':')
                }
                line.startsWith("#EXTINF") -> {
                    pendingDuration = EXTINF.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                }
                !line.startsWith("#") -> {
                    val duration = pendingDuration ?: 0.0
                    val url = ManifestSniffer.resolveUrl(manifestUrl, line)
                    val range = pendingRange?.let {
                        parseRange(it, previousSegmentRangeEnd?.takeIf { previousSegmentRangeUrl == url })
                    }
                    if (pendingRange != null && range == null) unsupportedReason = "Invalid HLS segment byte range"
                    segments.add(
                        HlsSegment(
                            url = url,
                            durationSeconds = duration,
                            sequenceNumber = sequenceNumber,
                            keyUri = currentKeyUri,
                            ivHex = currentIvHex,
                            byteRange = range,
                            initialization = currentMap
                        )
                    )
                    total += duration
                    sequenceNumber++
                    pendingDuration = null
                    pendingRange = null
                    previousSegmentRangeUrl = url
                    previousSegmentRangeEnd = range?.endInclusive
                }
            }
        }
        return HlsLeafPlaylist(segments, total, unsupportedReason)
    }
}
