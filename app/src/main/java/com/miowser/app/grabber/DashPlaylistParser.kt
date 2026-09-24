package com.miowser.app.grabber

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

data class DashByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1L
}

data class DashSegment(val url: String, val byteRange: DashByteRange? = null)

data class DashSegmentBase(
    val mediaUrl: String,
    val indexUrl: String,
    val indexRange: DashByteRange?,
    val initializationUrl: String,
    val initializationRange: DashByteRange?,
    val expectedTimescale: Long?,
    val indexRangeExact: Boolean,
    val validationError: String? = null
)

data class DashTrack(
    val type: String, // "video", "audio", or "text"
    val bandwidth: Long?,
    val resolution: String?, // "WIDTHxHEIGHT", video only
    val initSegmentUrl: String?, // null when the Representation has no separate init segment
    val mediaSegments: List<DashSegment>,
    val initSegmentRange: DashByteRange? = null,
    val segmentBase: DashSegmentBase? = null
)

data class DashManifest(
    val tracks: List<DashTrack>,
    val unsupportedReason: String? = null,
    val durationSeconds: Double? = null,
    val representationCount: Int = tracks.size,
    val maxVideoResolution: String? = null,
    val maxVideoBandwidth: Long? = null,
    val maxAudioBandwidth: Long? = null
)

internal fun parseDashByteRange(value: String?): DashByteRange? {
    if (value.isNullOrBlank()) return null
    val match = Regex("^(\\d+)-(\\d+)$").matchEntire(value.trim()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    // end == Long.MAX_VALUE would overflow the inclusive length calculation and cannot describe
    // a practical HTTP resource on Android anyway.
    if (start < 0L || end < start || end == Long.MAX_VALUE) return null
    return DashByteRange(start, end)
}

/**
 * Full DASH MPD segment-list resolution for the downloader — as opposed to ManifestSniffer's
 * lightweight detection-time sniff (confirms type, counts Representations, samples nothing).
 * Ported from 1DM's own MPD handling (reverse-engineered; class `i.gy` in the original APK —
 * project memory: 1dm-grabber-mechanics.md), specifically its SegmentTemplate `$Number$`/`$Time$`
 * placeholder substitution (their pattern was literally `"\$" + name + "(%[\d]+d)?\$"`), its
 * BaseURL resolution, and its exact year/month/week/day-to-seconds constants for ISO-8601
 * duration parsing (31536000/2592000/604800/86400) — reimplemented with a real DOM parser
 * (`javax.xml.parsers`) rather than the decompiled obfuscated original.
 *
 * Supports the three common VOD addressing modes: SegmentTemplate (with an explicit
 * SegmentTimeline, or with duration+timescale+startNumber counting) and SegmentList's explicit
 * SegmentURL entries, plus SegmentBase metadata for the downloader's bounded `sidx` range
 * resolver. The XML parser never fetches bytes itself: it records the index/init locations and
 * validators, and DashDownloader resolves them only for the selected representations.
 */
object DashPlaylistParser {
    private val DURATION_DATE = Regex("(?:([\\d.]+)Y)?(?:([\\d.]+)M)?(?:([\\d.]+)W)?(?:([\\d.]+)D)?")
    private val DURATION_TIME = Regex("(?:([\\d.]+)H)?(?:([\\d.]+)M)?(?:([\\d.]+)S)?")

    fun parse(body: String, manifestUrl: String): DashManifest {
        val doc = parseXml(body) ?: return DashManifest(emptyList())
        val mpd = doc.documentElement ?: return DashManifest(emptyList())
        val periods = childElements(mpd, "Period")
        val periodDurations = periods.map { parseIsoDuration(it.getAttribute("duration")) }
        val mpdDurationSeconds = parseIsoDuration(mpd.getAttribute("mediaPresentationDuration"))
            ?: periodDurations.takeIf { it.isNotEmpty() && it.all { duration -> duration != null } }
                ?.sumOf { it!! }
        val representationCount = descendantElements(mpd, "Representation").size
        if (descendantElements(mpd, "ContentProtection").isNotEmpty()) {
            return DashManifest(
                tracks = emptyList(),
                unsupportedReason = "DRM-protected DASH streams are not supported",
                durationSeconds = mpdDurationSeconds,
                representationCount = representationCount
            )
        }
        val mpdBaseUrl = resolveBaseUrl(mpd, manifestUrl)

        val tracks = mutableListOf<DashTrack>()
        var maxVideoWidth: Int? = null
        var maxVideoHeight: Int? = null
        var maxVideoBandwidth: Long? = null
        var maxAudioBandwidth: Long? = null
        for (period in periods) {
            val periodBaseUrl = resolveBaseUrl(period, mpdBaseUrl)
            val periodDuration = period.getAttribute("duration").let(::parseIsoDuration) ?: mpdDurationSeconds
            for (adaptationSet in childElements(period, "AdaptationSet")) {
                val adaptationBaseUrl = resolveBaseUrl(adaptationSet, periodBaseUrl)
                val setContentType = adaptationSet.getAttribute("contentType").ifBlank { adaptationSet.getAttribute("mimeType") }
                val setSegmentTemplate = childElements(adaptationSet, "SegmentTemplate").firstOrNull()
                val setSegmentList = childElements(adaptationSet, "SegmentList").firstOrNull()
                val setSegmentBase = childElements(adaptationSet, "SegmentBase").firstOrNull()
                val setWidth = adaptationSet.getAttribute("width").toIntOrNull()
                    ?: adaptationSet.getAttribute("maxWidth").toIntOrNull()
                val setHeight = adaptationSet.getAttribute("height").toIntOrNull()
                    ?: adaptationSet.getAttribute("maxHeight").toIntOrNull()
                for (representation in childElements(adaptationSet, "Representation")) {
                    val repBaseUrl = resolveBaseUrl(representation, adaptationBaseUrl)
                    val mimeType = representation.getAttribute("mimeType").ifBlank { setContentType }
                    val type = classifyType(mimeType)
                    val bandwidth = representation.getAttribute("bandwidth").toLongOrNull()
                    val width = representation.getAttribute("width").toIntOrNull() ?: setWidth
                    val height = representation.getAttribute("height").toIntOrNull() ?: setHeight
                    val resolution = if (width != null && height != null) "${width}x${height}" else null
                    val repId = representation.getAttribute("id").ifBlank { null }

                    if (type == "video") {
                        if (width != null && height != null) {
                            val oldArea = (maxVideoWidth?.toLong() ?: 0L) * (maxVideoHeight?.toLong() ?: 0L)
                            val newArea = width.toLong() * height.toLong()
                            if (newArea > oldArea) {
                                maxVideoWidth = width
                                maxVideoHeight = height
                            }
                        }
                        if (bandwidth != null && bandwidth > (maxVideoBandwidth ?: -1L)) maxVideoBandwidth = bandwidth
                    } else if (type == "audio" && bandwidth != null && bandwidth > (maxAudioBandwidth ?: -1L)) {
                        maxAudioBandwidth = bandwidth
                    }

                    val segmentTemplate = childElements(representation, "SegmentTemplate").firstOrNull() ?: setSegmentTemplate
                    val segmentList = childElements(representation, "SegmentList").firstOrNull() ?: setSegmentList
                    val segmentBase = childElements(representation, "SegmentBase").firstOrNull() ?: setSegmentBase

                    val track = when {
                        segmentTemplate != null ->
                            buildFromSegmentTemplate(segmentTemplate, repBaseUrl, repId, bandwidth, periodDuration, type, resolution)
                        segmentList != null ->
                            buildFromSegmentList(segmentList, repBaseUrl, type, bandwidth, resolution)
                        segmentBase != null ->
                            buildFromSegmentBase(segmentBase, repBaseUrl, type, bandwidth, resolution)
                        else -> null
                    }
                    if (track != null && (track.mediaSegments.isNotEmpty() || track.segmentBase != null)) tracks.add(track)
                }
            }
        }
        return DashManifest(
            tracks = tracks,
            durationSeconds = mpdDurationSeconds,
            representationCount = representationCount,
            maxVideoResolution = if (maxVideoWidth != null && maxVideoHeight != null) {
                "${maxVideoWidth}x$maxVideoHeight"
            } else null,
            maxVideoBandwidth = maxVideoBandwidth,
            maxAudioBandwidth = maxAudioBandwidth
        )
    }

    private fun buildFromSegmentTemplate(
        segmentTemplate: Element,
        baseUrl: String,
        repId: String?,
        bandwidth: Long?,
        periodDurationSeconds: Double?,
        type: String,
        resolution: String?
    ): DashTrack {
        val initTemplate = segmentTemplate.getAttribute("initialization").ifBlank { null }
        val mediaTemplate = segmentTemplate.getAttribute("media").ifBlank { null }
            ?: return DashTrack(type, bandwidth, resolution, null, emptyList())
        val startNumber = segmentTemplate.getAttribute("startNumber").toLongOrNull() ?: 1L
        val timescale = segmentTemplate.getAttribute("timescale").toLongOrNull()?.takeIf { it > 0 } ?: 1L
        val duration = segmentTemplate.getAttribute("duration").toLongOrNull()
        val timeline = childElements(segmentTemplate, "SegmentTimeline").firstOrNull()

        val initUrl = initTemplate?.let {
            ManifestSniffer.resolveUrl(baseUrl, substituteTemplate(it, repId, bandwidth, null, null))
        }

        val segments = when {
            timeline != null -> buildFromSegmentTimeline(timeline, mediaTemplate, baseUrl, repId, bandwidth, startNumber)
            duration != null && duration > 0 && periodDurationSeconds != null -> {
                val segmentDurationSeconds = duration.toDouble() / timescale
                val count = ceil(periodDurationSeconds / segmentDurationSeconds).toLong().coerceAtLeast(1)
                (0 until count).map { i ->
                    val number = startNumber + i
                    DashSegment(ManifestSniffer.resolveUrl(baseUrl, substituteTemplate(mediaTemplate, repId, bandwidth, number, null)))
                }
            }
            else -> emptyList()
        }
        return DashTrack(type, bandwidth, resolution, initUrl, segments)
    }

    /** `<S t="start" d="duration" r="repeatCount"/>` entries — t/r default to continuing from the previous entry / zero. */
    private fun buildFromSegmentTimeline(
        timeline: Element,
        mediaTemplate: String,
        baseUrl: String,
        repId: String?,
        bandwidth: Long?,
        startNumber: Long
    ): List<DashSegment> {
        val segments = mutableListOf<DashSegment>()
        var currentTime = 0L
        var number = startNumber
        for (s in childElements(timeline, "S")) {
            val d = s.getAttribute("d").toLongOrNull() ?: continue
            s.getAttribute("t").toLongOrNull()?.let { currentTime = it }
            val repeatCount = s.getAttribute("r").toLongOrNull() ?: 0L
            for (i in 0..repeatCount) {
                segments.add(
                    DashSegment(ManifestSniffer.resolveUrl(baseUrl, substituteTemplate(mediaTemplate, repId, bandwidth, number, currentTime)))
                )
                currentTime += d
                number++
            }
        }
        return segments
    }

    private fun buildFromSegmentList(
        segmentList: Element,
        baseUrl: String,
        type: String,
        bandwidth: Long?,
        resolution: String?
    ): DashTrack {
        val initUrl = childElements(segmentList, "Initialization").firstOrNull()
            ?.getAttribute("sourceURL")?.ifBlank { null }
            ?.let { ManifestSniffer.resolveUrl(baseUrl, it) }
        val segments = childElements(segmentList, "SegmentURL").mapNotNull { el ->
            el.getAttribute("media").ifBlank { null }?.let { DashSegment(ManifestSniffer.resolveUrl(baseUrl, it)) }
        }
        return DashTrack(type, bandwidth, resolution, initUrl, segments)
    }

    private fun buildFromSegmentBase(
        segmentBase: Element,
        mediaUrl: String,
        type: String,
        bandwidth: Long?,
        resolution: String?
    ): DashTrack {
        val initialization = childElements(segmentBase, "Initialization").firstOrNull()
        val representationIndex = childElements(segmentBase, "RepresentationIndex").firstOrNull()
        val initUrl = initialization?.getAttribute("sourceURL")?.ifBlank { null }
            ?.let { ManifestSniffer.resolveUrl(mediaUrl, it) }
            ?: mediaUrl
        val indexUrl = representationIndex?.getAttribute("sourceURL")?.ifBlank { null }
            ?.let { ManifestSniffer.resolveUrl(mediaUrl, it) }
            ?: mediaUrl
        val indexRangeText = segmentBase.getAttribute("indexRange").ifBlank {
            representationIndex?.getAttribute("range").orEmpty()
        }
        val indexRange = parseDashByteRange(indexRangeText)
        val initRangeText = initialization?.getAttribute("range").orEmpty()
        val initRange = parseDashByteRange(initRangeText)
        val validationError = when {
            indexRangeText.isBlank() -> "SegmentBase is missing indexRange"
            indexRange == null -> "SegmentBase has an invalid indexRange"
            initRangeText.isNotBlank() && initRange == null -> "SegmentBase has an invalid initialization range"
            else -> null
        }
        return DashTrack(
            type = type,
            bandwidth = bandwidth,
            resolution = resolution,
            initSegmentUrl = null,
            mediaSegments = emptyList(),
            segmentBase = DashSegmentBase(
                mediaUrl = mediaUrl,
                indexUrl = indexUrl,
                indexRange = indexRange,
                initializationUrl = initUrl,
                initializationRange = initRange,
                expectedTimescale = segmentBase.getAttribute("timescale").toLongOrNull()?.takeIf { it > 0L },
                indexRangeExact = segmentBase.getAttribute("indexRangeExact").equals("true", ignoreCase = true),
                validationError = validationError
            )
        )
    }

    // Matches 1DM's own substitution pattern exactly: "$" + name + optional "%0Nd" width spec + "$".
    private fun substituteTemplate(template: String, repId: String?, bandwidth: Long?, number: Long?, time: Long?): String {
        var result = template
        result = substituteVar(result, "RepresentationID", repId)
        result = substituteVar(result, "Bandwidth", bandwidth?.toString())
        result = substituteVar(result, "Number", number?.toString())
        result = substituteVar(result, "Time", time?.toString())
        return result
    }

    private fun substituteVar(template: String, name: String, rawValue: String?): String {
        if (rawValue == null) return template
        val pattern = Regex("\\$$name(?:%0(\\d+)d)?\\$")
        return pattern.replace(template) { match ->
            val width = match.groupValues[1].toIntOrNull()
            if (width != null) rawValue.padStart(width, '0') else rawValue
        }
    }

    private fun resolveBaseUrl(element: Element, parentBaseUrl: String): String {
        val baseUrlText = childElements(element, "BaseURL").firstOrNull()?.textContent?.trim()
        if (baseUrlText.isNullOrEmpty()) return parentBaseUrl
        return ManifestSniffer.resolveUrl(parentBaseUrl, baseUrlText)
    }

    private fun childElements(parent: Element, tagName: String): List<Element> {
        val children = parent.childNodes
        val result = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && elementName(node).equals(tagName, ignoreCase = true)) result.add(node)
        }
        return result
    }

    private fun descendantElements(parent: Element, tagName: String): List<Element> {
        val nodes = parent.getElementsByTagName("*")
        val result = mutableListOf<Element>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && elementName(node).equals(tagName, ignoreCase = true)) result.add(node)
        }
        return result
    }

    private fun elementName(element: Element): String =
        element.localName ?: element.tagName.substringAfter(':')

    // Mirrors 1DM's own classifier exactly: startsWith("video") / contains("audio") / contains("text") / default video.
    private fun classifyType(mimeOrContentType: String): String = when {
        mimeOrContentType.startsWith("video") -> "video"
        mimeOrContentType.contains("audio") -> "audio"
        mimeOrContentType.contains("text") -> "text"
        else -> "video"
    }

    private fun parseXml(body: String): Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Best-effort XXE hardening for untrusted remote content — if this platform's parser
            // doesn't recognize the feature name, ignore rather than fail the whole parse over it.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    // Same year/month/week/day-to-seconds approximations 1DM's own duration parser uses
    // (365/30/7/1-day constants) — exact for VOD content, where these units are rarely used
    // anyway (real durations are almost always PT#H#M#S).
    private fun parseIsoDuration(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val upper = value.uppercase().removePrefix("P")
        val timeIndex = upper.indexOf('T')
        val datePart = if (timeIndex >= 0) upper.substring(0, timeIndex) else upper
        val timePart = if (timeIndex >= 0) upper.substring(timeIndex + 1) else ""
        val dateMatch = DURATION_DATE.matchEntire(datePart)
        val timeMatch = DURATION_TIME.matchEntire(timePart)
        val years = dateMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val months = dateMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
        val weeks = dateMatch?.groupValues?.get(3)?.toDoubleOrNull() ?: 0.0
        val days = dateMatch?.groupValues?.get(4)?.toDoubleOrNull() ?: 0.0
        val hours = timeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val minutes = timeMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
        val seconds = timeMatch?.groupValues?.get(3)?.toDoubleOrNull() ?: 0.0
        val total = years * 31536000.0 + months * 2592000.0 + weeks * 604800.0 + days * 86400.0 +
            hours * 3600.0 + minutes * 60.0 + seconds
        return total.takeIf { it > 0 }
    }
}
