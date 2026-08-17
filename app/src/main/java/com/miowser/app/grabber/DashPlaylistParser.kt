package com.miowser.app.grabber

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

data class DashSegment(val url: String)

data class DashTrack(
    val type: String, // "video", "audio", or "text"
    val bandwidth: Long?,
    val resolution: String?, // "WIDTHxHEIGHT", video only
    val initSegmentUrl: String?, // null when the Representation has no separate init segment
    val mediaSegments: List<DashSegment>
)

data class DashManifest(val tracks: List<DashTrack>)

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
 * Supports the two common VOD addressing modes: SegmentTemplate (with an explicit
 * SegmentTimeline, or with duration+timescale+startNumber counting) and SegmentList's explicit
 * SegmentURL entries. Does not support SegmentBase's single-file byte-range (sidx-indexed)
 * addressing — that needs parsing an MP4 sidx box out of a partial byte-range fetch during
 * manifest resolution itself, a materially bigger lift than the XML-only cases here.
 */
object DashPlaylistParser {
    private val DURATION_DATE = Regex("(?:([\\d.]+)Y)?(?:([\\d.]+)M)?(?:([\\d.]+)W)?(?:([\\d.]+)D)?")
    private val DURATION_TIME = Regex("(?:([\\d.]+)H)?(?:([\\d.]+)M)?(?:([\\d.]+)S)?")

    fun parse(body: String, manifestUrl: String): DashManifest {
        val doc = parseXml(body) ?: return DashManifest(emptyList())
        val mpd = doc.documentElement ?: return DashManifest(emptyList())
        val mpdBaseUrl = resolveBaseUrl(mpd, manifestUrl)
        val mpdDurationSeconds = parseIsoDuration(mpd.getAttribute("mediaPresentationDuration"))

        val tracks = mutableListOf<DashTrack>()
        for (period in childElements(mpd, "Period")) {
            val periodBaseUrl = resolveBaseUrl(period, mpdBaseUrl)
            val periodDuration = period.getAttribute("duration").let(::parseIsoDuration) ?: mpdDurationSeconds
            for (adaptationSet in childElements(period, "AdaptationSet")) {
                val adaptationBaseUrl = resolveBaseUrl(adaptationSet, periodBaseUrl)
                val setContentType = adaptationSet.getAttribute("contentType").ifBlank { adaptationSet.getAttribute("mimeType") }
                val setSegmentTemplate = childElements(adaptationSet, "SegmentTemplate").firstOrNull()
                for (representation in childElements(adaptationSet, "Representation")) {
                    val repBaseUrl = resolveBaseUrl(representation, adaptationBaseUrl)
                    val mimeType = representation.getAttribute("mimeType").ifBlank { setContentType }
                    val type = classifyType(mimeType)
                    val bandwidth = representation.getAttribute("bandwidth").toLongOrNull()
                    val width = representation.getAttribute("width").toIntOrNull()
                    val height = representation.getAttribute("height").toIntOrNull()
                    val resolution = if (width != null && height != null) "${width}x${height}" else null
                    val repId = representation.getAttribute("id").ifBlank { null }

                    val segmentTemplate = childElements(representation, "SegmentTemplate").firstOrNull() ?: setSegmentTemplate
                    val segmentList = childElements(representation, "SegmentList").firstOrNull()

                    val track = when {
                        segmentTemplate != null ->
                            buildFromSegmentTemplate(segmentTemplate, repBaseUrl, repId, bandwidth, periodDuration, type, resolution)
                        segmentList != null ->
                            buildFromSegmentList(segmentList, repBaseUrl, type, bandwidth, resolution)
                        else -> null // e.g. SegmentBase — not supported, see class doc comment
                    }
                    if (track != null && track.mediaSegments.isNotEmpty()) tracks.add(track)
                }
            }
        }
        return DashManifest(tracks)
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
            if (node is Element && node.tagName.equals(tagName, ignoreCase = true)) result.add(node)
        }
        return result
    }

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
