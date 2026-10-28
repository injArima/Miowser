package com.miowser.app.player

import com.miowser.app.grabber.HlsRendition
import com.miowser.app.grabber.resolutionHeight
import java.util.Base64

internal fun syntheticHlsMaster(renditions: List<HlsRendition>): String? {
    val unique = renditions.filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .distinctBy(HlsRendition::url)
    if (unique.size < 2) return null
    return buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        unique.forEach { rendition ->
            val bandwidth = rendition.bitrateBitsPerSecond?.takeIf { it > 0L }
                ?: estimatedHlsBandwidth(resolutionHeight(rendition.resolution))
            append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
            rendition.resolution?.takeIf { Regex("^\\d+[xX]\\d+$").matches(it) }?.let {
                append(",RESOLUTION=").append(it.lowercase())
            }
            appendLine()
            appendLine(rendition.url.replace("\r", "").replace("\n", ""))
        }
    }
}

internal fun syntheticHlsMasterDataUri(renditions: List<HlsRendition>): String? =
    syntheticHlsMaster(renditions)?.toByteArray(Charsets.UTF_8)?.let { bytes ->
        "data:application/vnd.apple.mpegurl;base64," + Base64.getEncoder().encodeToString(bytes)
    }

private fun estimatedHlsBandwidth(height: Int?): Long = when {
    height == null -> 1_000_000L
    height >= 2160 -> 14_000_000L
    height >= 1440 -> 8_000_000L
    height >= 1080 -> 5_000_000L
    height >= 720 -> 2_500_000L
    height >= 480 -> 1_200_000L
    height >= 360 -> 700_000L
    else -> 400_000L
}
