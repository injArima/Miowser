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
    val firstSegmentDurationSeconds: Double? // HLS leaf only: that one segment's own #EXTINF duration
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
    private val DASH_REPRESENTATION = Regex("<Representation\\b")
    private val DASH_CONTENT_PROTECTION = Regex("<ContentProtection\\b")
    private val DASH_DURATION = Regex("mediaPresentationDuration=\"PT(?:([\\d.]+)H)?(?:([\\d.]+)M)?(?:([\\d.]+)S)?\"")
    // Assumes width= appears before height= within a Representation tag, which real DASH
    // manifests consistently do; a full XML attribute parser would be needed to lift that
    // assumption, which is more than this line-scanning sniffer takes on elsewhere. The leading
    // \s is load-bearing, not decorative: without it this also matches inside "bandwidth=" (a
    // real attribute on the same tag), since "width=" is a substring of "bandwidth=" — caught by
    // a test fixture that put both attributes on the same Representation, like a real manifest
    // does.
    private val DASH_RESOLUTION = Regex("\\swidth=\"(\\d+)\"[^>]*\\sheight=\"(\\d+)\"")

    // Multiple representations each advertise their own resolution; the highest one is the most
    // useful single number to surface (matches what a user means by "the video's resolution" for
    // an adaptive stream — its best available quality). HLS has its own variant-aware version of
    // this below, since it also needs to remember *which* variant that was (for bestVariantUrl).
    private fun Regex.highestResolution(body: String): String? =
        findAll(body)
            .mapNotNull { m -> m.groupValues[1].toIntOrNull()?.let { w -> m.groupValues[2].toIntOrNull()?.let { h -> w to h } } }
            .maxByOrNull { (w, h) -> w * h }
            ?.let { (w, h) -> "${w}x${h}" }

    private fun resolveUrl(base: String, relativeOrAbsolute: String): String =
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
            trimmed.startsWith("<?xml") && body.contains("<MPD") -> sniffDash(body)
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
        var firstSegmentUrl: String? = null
        var firstSegmentDuration: Double? = null
        var pendingSegmentDuration: Double? = null

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingBandwidth = HLS_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull()
                    pendingResolution = HLS_RESOLUTION.find(line)?.let { "${it.groupValues[1]}x${it.groupValues[2]}" }
                    awaitingVariantUri = true
                }
                line.startsWith("#EXTINF") -> {
                    pendingSegmentDuration = HLS_EXTINF.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
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
                        segmentCount++
                        pendingSegmentDuration = null
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
        return ManifestInfo(
            confirmedType = MediaType.HLS,
            isMasterOrMultiVariant = variants.isNotEmpty(),
            variantCount = variants.size,
            isEncrypted = isEncrypted,
            durationSeconds = if (isLeaf && segmentCount > 0) totalSegmentDuration else null,
            resolution = best?.resolution,
            bestVariantUrl = best?.url,
            firstSegmentUrl = if (isLeaf) firstSegmentUrl else null,
            firstSegmentDurationSeconds = if (isLeaf) firstSegmentDuration else null
        )
    }

    private fun sniffDash(body: String): ManifestInfo {
        val variantCount = DASH_REPRESENTATION.findAll(body).count()
        val isEncrypted = DASH_CONTENT_PROTECTION.containsMatchIn(body)
        val duration = DASH_DURATION.find(body)?.let { m ->
            val hours = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val minutes = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val seconds = m.groupValues[3].toDoubleOrNull() ?: 0.0
            hours * 3600 + minutes * 60 + seconds
        }
        return ManifestInfo(
            confirmedType = MediaType.DASH,
            isMasterOrMultiVariant = variantCount > 1,
            variantCount = variantCount,
            isEncrypted = isEncrypted,
            durationSeconds = duration,
            resolution = DASH_RESOLUTION.highestResolution(body),
            bestVariantUrl = null,
            firstSegmentUrl = null,
            firstSegmentDurationSeconds = null
        )
    }
}
