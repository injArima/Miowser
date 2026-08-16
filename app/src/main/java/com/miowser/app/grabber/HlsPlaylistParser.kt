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
    val ivHex: String? // explicit "IV=0x..." from the #EXT-X-KEY tag; null = derive from sequenceNumber
)

data class HlsLeafPlaylist(val segments: List<HlsSegment>, val totalDurationSeconds: Double)

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

    fun parseLeaf(body: String, manifestUrl: String): HlsLeafPlaylist {
        var sequenceNumber = MEDIA_SEQUENCE.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        var currentKeyUri: String? = null
        var currentIvHex: String? = null
        var pendingDuration: Double? = null

        val segments = mutableListOf<HlsSegment>()
        var total = 0.0

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val method = KEY_METHOD.find(line)?.groupValues?.get(1)
                    currentKeyUri = if (method == null || method.equals("NONE", ignoreCase = true)) {
                        null
                    } else {
                        KEY_URI.find(line)?.groupValues?.get(1)?.let { ManifestSniffer.resolveUrl(manifestUrl, it) }
                    }
                    currentIvHex = KEY_IV.find(line)?.groupValues?.get(1)
                }
                line.startsWith("#EXTINF") -> {
                    pendingDuration = EXTINF.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                }
                !line.startsWith("#") -> {
                    val duration = pendingDuration ?: 0.0
                    segments.add(
                        HlsSegment(
                            url = ManifestSniffer.resolveUrl(manifestUrl, line),
                            durationSeconds = duration,
                            sequenceNumber = sequenceNumber,
                            keyUri = currentKeyUri,
                            ivHex = currentIvHex
                        )
                    )
                    total += duration
                    sequenceNumber++
                    pendingDuration = null
                }
            }
        }
        return HlsLeafPlaylist(segments, total)
    }
}
