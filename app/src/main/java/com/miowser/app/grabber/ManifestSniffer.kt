package com.miowser.app.grabber

import java.net.URL

/** What a fetched manifest's actual content told us — confirming (or rejecting) the URL's extension. */
data class ManifestInfo(
    val confirmedType: MediaType?, // null = the body didn't actually look like HLS or DASH
    val isMasterOrMultiVariant: Boolean,
    val variantCount: Int,
    val isEncrypted: Boolean,
    val durationSeconds: Double?, // a leaf playlist's own #EXTINF sum; null for a master (it has no segments of its own)
    val resolution: String?, // "WIDTHxHEIGHT" of the best variant/representation advertised, if any
    // The following three back MediaProbe's size estimate — see its doc comment and project
    // memory 1dm-grabber-mechanics.md / RemoteFile.getApproxLengthFromThreadInfo for where this
    // technique (sample one real segment, extrapolate by duration) comes from. Only ever
    // populated for HLS: DASH's segment addressing (SegmentTemplate/SegmentList/SegmentBase) has
    // enough different forms that reliably resolving "the first segment's URL" needs a real MPD
    // parser, which this line-scanning sniffer deliberately isn't.
    val bestVariantUrl: String?, // HLS master only: absolute URL of the [resolution]'s own leaf playlist
    val firstSegmentUrl: String?, // HLS leaf only: absolute URL of its first media segment
    val firstSegmentDurationSeconds: Double?, // HLS leaf only: that one segment's own #EXTINF duration
    val sampleSegments: List<ManifestSegmentSample> = emptyList(),
    val declaredSizeBytes: Long? = null,
    val bitrateBitsPerSecond: Long? = null
)

data class ManifestSegmentSample(
    val url: String,
    val durationSeconds: Double,
    val declaredByteLength: Long? = null
)

/**
 * Confirms a fetched manifest body is real HLS/DASH by content, not just extension — the same
 * body-magic-byte principle validated against 1DM's own grabber (project memory:
 * 1dm-grabber-mechanics.md: "#EXTM3U" / "<AdaptationSet"/"mediaPresentationDuration" rather than
 * trusting Content-Type or the URL). Deliberately simple regex/line-scanning rather than a full
 * playlist/MPD parser — enough to confirm type, count variants, flag encryption, read the
 * advertised duration, and (for HLS) locate one real segment to sample for MediaProbe's size
 * estimate, without a dependency for full manifest parsing.
 */
object ManifestSniffer {
    private val HLS_KEY_LINE = Regex("#EXT-X-KEY:[^\\n\\r]*")
    private val HLS_BANDWIDTH = Regex("BANDWIDTH=(\\d+)")
    private val HLS_RESOLUTION = Regex("RESOLUTION=(\\d+)x(\\d+)")
    private val HLS_EXTINF = Regex("#EXTINF:([\\d.]+)")
    private val DASH_ROOT = Regex(
        "^(?:<\\?xml[^>]*>\\s*)?(?:<!--.*?-->\\s*)*<(?:[A-Za-z_][\\w.-]*:)?MPD(?:\\s|>)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HLS_AVERAGE_BANDWIDTH = Regex("AVERAGE-BANDWIDTH=(\\d+)")
    private val HLS_BYTE_RANGE = Regex("#EXT-X-BYTERANGE:(\\d+)(?:@\\d+)?")
    private val HLS_MAP_BYTE_RANGE = Regex("BYTERANGE=\"(\\d+)(?:@\\d+)?\"")

    // internal, not private: HlsPlaylistParser (the downloader's full segment-list parser, as
    // opposed to this file's lightweight detection-time sniff) resolves segment/key URIs the
    // same way and shouldn't duplicate this logic.
    internal fun resolveUrl(base: String, relativeOrAbsolute: String): String =
        runCatching { URL(URL(base), relativeOrAbsolute).toString() }.getOrDefault(relativeOrAbsolute)

    private fun areaOf(resolution: String): Long {
        val w = resolution.substringBefore('x').toLongOrNull() ?: return -1L
        val h = resolution.substringAfter('x').toLongOrNull() ?: return -1L
        return w * h
    }

    fun sniff(body: String, manifestUrl: String): ManifestInfo {
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("#EXTM3U") -> sniffHls(body, manifestUrl)
            // The XML declaration is optional per spec, and real-world MPDs routinely omit it —
            // confirmed on-device that requiring "<?xml" as the literal prefix was the actual
            // cause of DASH detection never firing at all: Akamai's own bbb_30fps.mpd test
            // stream (and most real CDN-served MPDs) starts directly with "<MPD", no prolog.
            // <MPD is checked as the trimmed body's own prefix for that common case, and — when a
            // prolog IS present — within just the first slice of the body rather than an
            // unbounded scan, so an unrelated document that merely mentions "<MPD" somewhere deep
            // inside (a forum post about MPEG-DASH, say) doesn't false-positive.
            DASH_ROOT.find(trimmed.take(500)) != null -> sniffDash(body, manifestUrl)
            else -> ManifestInfo(
                confirmedType = null,
                isMasterOrMultiVariant = false,
                variantCount = 0,
                isEncrypted = false,
                durationSeconds = null,
                resolution = null,
                bestVariantUrl = null,
                firstSegmentUrl = null,
                firstSegmentDurationSeconds = null
            )
        }
    }

    private data class HlsVariant(val bandwidth: Long?, val resolution: String?, val url: String)

    private fun sniffHls(body: String, manifestUrl: String): ManifestInfo {
        val isEncrypted = HLS_KEY_LINE.findAll(body).any { !it.value.contains("METHOD=NONE") }

        // A master/multivariant playlist lists other playlists (one per quality); a leaf/media
        // playlist lists actual media segments. Parsed together in one line-by-line pass, since
        // both a variant's resolution and a segment's own duration only make sense read
        // alongside the very next non-comment line (its URI) — standalone regexes over the whole
        // body can't capture that pairing, or resolve a relative URI against its own line.
        val variants = mutableListOf<HlsVariant>()
        var pendingBandwidth: Long? = null
        var pendingResolution: String? = null
        var awaitingVariantUri = false

        var segmentCount = 0
        var totalSegmentDuration = 0.0
        var totalDeclaredBytes = 0L
        var allSegmentsHaveDeclaredSize = true
        var mapDeclaredBytes = 0L
        var hasMap = false
        var mapHasDeclaredSize = true
        var mapCount = 0
        var firstSegmentUrl: String? = null
        var firstSegmentDuration: Double? = null
        var pendingSegmentDuration: Double? = null
        var pendingSegmentByteLength: Long? = null
        val segmentSamples = mutableListOf<ManifestSegmentSample>()

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingBandwidth = HLS_AVERAGE_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull()
                        ?: HLS_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull()
                    pendingResolution = HLS_RESOLUTION.find(line)?.let { "${it.groupValues[1]}x${it.groupValues[2]}" }
                    awaitingVariantUri = true
                }
                line.startsWith("#EXTINF") -> {
                    pendingSegmentDuration = HLS_EXTINF.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
                }
                line.startsWith("#EXT-X-BYTERANGE") -> {
                    pendingSegmentByteLength = HLS_BYTE_RANGE.find(line)?.groupValues?.get(1)?.toLongOrNull()
                }
                line.startsWith("#EXT-X-MAP") -> {
                    hasMap = true
                    mapCount++
                    val mapLength = HLS_MAP_BYTE_RANGE.find(line)?.groupValues?.get(1)?.toLongOrNull()
                    // Multiple initialization maps can change over a playlist. Treat that layout
                    // as estimated rather than risk claiming an exact size while deduplicating or
                    // double-counting map bytes incorrectly.
                    mapHasDeclaredSize = mapCount == 1 && mapLength != null
                    mapDeclaredBytes = mapLength ?: 0L
                }
                !line.startsWith("#") -> when {
                    awaitingVariantUri -> {
                        variants.add(HlsVariant(pendingBandwidth, pendingResolution, resolveUrl(manifestUrl, line)))
                        awaitingVariantUri = false
                        pendingBandwidth = null
                        pendingResolution = null
                    }
                    pendingSegmentDuration != null -> {
                        val duration = pendingSegmentDuration!!
                        totalSegmentDuration += duration
                        if (segmentCount == 0) {
                            firstSegmentUrl = resolveUrl(manifestUrl, line)
                            firstSegmentDuration = duration
                        }
                        val byteLength = pendingSegmentByteLength
                        if (byteLength == null) allSegmentsHaveDeclaredSize = false
                        else totalDeclaredBytes = runCatching { Math.addExact(totalDeclaredBytes, byteLength) }
                            .getOrElse {
                                allSegmentsHaveDeclaredSize = false
                                totalDeclaredBytes
                            }
                        segmentSamples.add(
                            ManifestSegmentSample(
                                url = resolveUrl(manifestUrl, line),
                                durationSeconds = duration,
                                declaredByteLength = byteLength
                            )
                        )
                        segmentCount++
                        pendingSegmentDuration = null
                        pendingSegmentByteLength = null
                    }
                }
            }
        }

        // Highest resolution wins (missing resolution sorts last); bandwidth breaks ties or
        // stands in when no variant advertises RESOLUTION at all.
        val best = variants.maxWithOrNull(
            compareBy({ it.resolution?.let(::areaOf) ?: -1L }, { it.bandwidth ?: -1L })
        )

        val isLeaf = variants.isEmpty()
        val representativeSamples = if (segmentSamples.size <= 3) {
            segmentSamples
        } else {
            listOf(segmentSamples.first(), segmentSamples[segmentSamples.size / 2], segmentSamples.last())
                .distinctBy { it.url to it.durationSeconds }
        }
        val declaredSize = if (isLeaf && segmentCount > 0 && allSegmentsHaveDeclaredSize && (!hasMap || mapHasDeclaredSize)) {
            runCatching { Math.addExact(totalDeclaredBytes, mapDeclaredBytes) }.getOrNull()
        } else null
        return ManifestInfo(
            confirmedType = MediaType.HLS,
            isMasterOrMultiVariant = variants.isNotEmpty(),
            variantCount = variants.size,
            isEncrypted = isEncrypted,
            durationSeconds = if (isLeaf && segmentCount > 0) totalSegmentDuration else null,
            resolution = best?.resolution,
            bestVariantUrl = best?.url,
            firstSegmentUrl = if (isLeaf) firstSegmentUrl else null,
            firstSegmentDurationSeconds = if (isLeaf) firstSegmentDuration else null,
            sampleSegments = if (isLeaf) representativeSamples else emptyList(),
            declaredSizeBytes = declaredSize,
            bitrateBitsPerSecond = best?.bandwidth
        )
    }

    private fun sniffDash(body: String, manifestUrl: String): ManifestInfo {
        val manifest = DashPlaylistParser.parse(body, manifestUrl)
        val variantCount = manifest.representationCount
        val isEncrypted = manifest.unsupportedReason?.contains("DRM", ignoreCase = true) == true
        val bitrate = listOfNotNull(manifest.maxVideoBandwidth, manifest.maxAudioBandwidth)
            .takeIf { it.isNotEmpty() }
            ?.fold(0L) { total, value -> runCatching { Math.addExact(total, value) }.getOrDefault(Long.MAX_VALUE) }
        return ManifestInfo(
            confirmedType = MediaType.DASH,
            isMasterOrMultiVariant = variantCount > 1,
            variantCount = variantCount,
            isEncrypted = isEncrypted,
            durationSeconds = manifest.durationSeconds,
            resolution = manifest.maxVideoResolution,
            bestVariantUrl = null,
            firstSegmentUrl = null,
            firstSegmentDurationSeconds = null,
            bitrateBitsPerSecond = bitrate
        )
    }
}
