package com.miowser.app.grabber

enum class MediaType { HLS, DASH, MP4, WEBM, MATROSKA, AUDIO, SUBTITLE, UNKNOWN }
enum class MediaSizeKind { EXACT, ESTIMATED, UNKNOWN }

/** One independently detected HLS media playlist that belongs to an adaptive rendition group. */
data class HlsRendition(
    val url: String,
    val resolution: String? = null,
    val bitrateBitsPerSecond: Long? = null
)

/** One detected candidate. See [com.miowser.app.grabber.MediaProbe] for how [isConfirmed] is decided. */
data class DetectedMedia(
    val url: String,
    val pageUrl: String,
    val type: MediaType,
    val isConfirmed: Boolean, // true = verified by fetching+sniffing the body/headers; false = a guess we couldn't check
    val sizeBytes: Long?, // exact for a direct file's Content-Length/-Range; for HLS an *estimate* extrapolated
                          // from one real sampled segment — see MediaProbe and isSizeApproximate
    val isSizeApproximate: Boolean, // true = sizeBytes is the sampled-segment estimate, not a verified total;
                                     // ported from 1DM's own RemoteFile.getApproxLengthFromThreadInfo, which
                                     // shows the same kind of number with a "~" prefix rather than claiming exactness
    val durationSeconds: Double?,
    val variantCount: Int?,
    val resolution: String?, // "WIDTHxHEIGHT" — from the manifest's best variant, or a sniffed <video> element's
                              // own dimensions; never known for a direct file from headers alone
    val isEncrypted: Boolean,
    val pageTitle: String?, // the tab's page title at detection time; used when the response itself
                             // does not provide a trustworthy filename
    val responseFileName: String? = null, // trustworthy filename resolved from response metadata/body sample
    val mimeType: String? = null,
    val bitrateBitsPerSecond: Long? = null,
    val finalUrl: String? = null,
    val requestReferer: String? = null,
    val requestOrigin: String? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    /** Absolute poster URL reported by the page's media element, when one exists. */
    val posterUrl: String? = null,
    /** Empty for ordinary media and real master playlists; populated for grouped HLS leaf lists. */
    val hlsRenditions: List<HlsRendition> = emptyList()
) {
    val sizeKind: MediaSizeKind
        get() = when {
            sizeBytes == null -> MediaSizeKind.UNKNOWN
            isSizeApproximate -> MediaSizeKind.ESTIMATED
            else -> MediaSizeKind.EXACT
    }
}

/**
 * Combines facts learned independently by the network probe and the live media element. Detection
 * is intentionally incremental: response headers know exact byte size/name/MIME, manifests know
 * available qualities and bitrate, and the element often knows the most reliable duration and
 * decoded dimensions. A later partial report must not erase an earlier useful field.
 */
internal fun mergeDetectedMedia(current: DetectedMedia, update: DetectedMedia): DetectedMedia {
    require(current.url == update.url)

    val (sizeBytes, approximate) = when {
        update.sizeBytes != null && !update.isSizeApproximate -> update.sizeBytes to false
        current.sizeBytes != null && !current.isSizeApproximate -> current.sizeBytes to false
        update.sizeBytes != null -> update.sizeBytes to true
        current.sizeBytes != null -> current.sizeBytes to current.isSizeApproximate
        else -> null to false
    }
    return current.copy(
        pageUrl = update.pageUrl.ifBlank { current.pageUrl },
        type = when {
            update.type != MediaType.UNKNOWN -> update.type
            else -> current.type
        },
        isConfirmed = current.isConfirmed || update.isConfirmed,
        sizeBytes = sizeBytes,
        isSizeApproximate = approximate,
        durationSeconds = update.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
            ?: current.durationSeconds,
        variantCount = listOfNotNull(current.variantCount, update.variantCount).maxOrNull(),
        resolution = betterResolution(current.resolution, update.resolution),
        isEncrypted = current.isEncrypted || update.isEncrypted,
        pageTitle = usefulText(update.pageTitle) ?: usefulText(current.pageTitle),
        responseFileName = usefulText(update.responseFileName) ?: usefulText(current.responseFileName),
        mimeType = usefulText(update.mimeType) ?: usefulText(current.mimeType),
        bitrateBitsPerSecond = listOfNotNull(current.bitrateBitsPerSecond, update.bitrateBitsPerSecond).maxOrNull(),
        finalUrl = usefulText(update.finalUrl) ?: usefulText(current.finalUrl),
        requestReferer = usefulText(update.requestReferer) ?: usefulText(current.requestReferer),
        requestOrigin = usefulText(update.requestOrigin) ?: usefulText(current.requestOrigin),
        detectedAt = minOf(current.detectedAt, update.detectedAt),
        posterUrl = usefulText(update.posterUrl) ?: usefulText(current.posterUrl),
        hlsRenditions = if (update.hlsRenditions.isNotEmpty()) update.hlsRenditions
            else current.hlsRenditions
    )
}

private fun usefulText(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)

private fun betterResolution(first: String?, second: String?): String? {
    fun area(value: String?): Long? {
        if (value == null) return null
        val match = Regex("^(\\d+)[xX](\\d+)$").matchEntire(value.trim()) ?: return null
        val width = match.groupValues[1].toLongOrNull() ?: return null
        val height = match.groupValues[2].toLongOrNull() ?: return null
        return runCatching { Math.multiplyExact(width, height) }.getOrNull()
    }
    val firstArea = area(first)
    val secondArea = area(second)
    return when {
        secondArea != null && (firstArea == null || secondArea >= firstArea) -> second
        first != null -> first
        else -> second
    }
}
