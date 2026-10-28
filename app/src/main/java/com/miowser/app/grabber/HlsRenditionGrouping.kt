package com.miowser.app.grabber

import java.net.URI
import java.util.Locale
import kotlin.math.max

/**
 * Collapses only high-confidence, separately detected HLS leaf playlists. A real multivariant
 * playlist is already one Media3 source and is deliberately left alone.
 */
internal fun groupHlsRenditions(items: List<DetectedMedia>): List<DetectedMedia> {
    if (items.size < 2) return items
    val families = items.withIndex().mapNotNull { indexed ->
        renditionFamilyKey(indexed.value)?.let { it to indexed }
    }.groupBy({ it.first }, { it.second })
    val groupedAt = mutableMapOf<Int, DetectedMedia>()
    val consumed = mutableSetOf<Int>()

    families.values.forEach { family ->
        val remaining = family.sortedBy { it.index }.toMutableList()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val cluster = mutableListOf(seed)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (samePresentationDuration(seed.value, candidate.value)) {
                    cluster += candidate
                    iterator.remove()
                }
            }
            val renditions = cluster.map { it.value }.distinctBy(DetectedMedia::url)
            if (renditions.size < 2 || renditionQualityCount(renditions) < 2) continue
            val representative = renditions.maxWithOrNull(compareBy<DetectedMedia>(
                { resolutionHeight(it.resolution) ?: 0 },
                { it.bitrateBitsPerSecond ?: 0L }
            )) ?: continue
            val group = renditions.sortedWith(compareByDescending<DetectedMedia> {
                resolutionHeight(it.resolution) ?: 0
            }.thenByDescending { it.bitrateBitsPerSecond ?: 0L })
            val outputIndex = cluster.minOf { it.index }
            groupedAt[outputIndex] = representative.copy(
                variantCount = group.size,
                hlsRenditions = group.map {
                    HlsRendition(it.url, it.resolution, it.bitrateBitsPerSecond)
                }
            )
            consumed += cluster.map { it.index }
        }
    }

    if (groupedAt.isEmpty()) return items
    return buildList(items.size) {
        items.indices.forEach { index ->
            val grouped = groupedAt[index]
            if (grouped != null) add(grouped)
            else if (index !in consumed) add(items[index])
        }
    }
}

private fun renditionFamilyKey(item: DetectedMedia): String? {
    if (item.type != MediaType.HLS || !item.isConfirmed || item.isEncrypted ||
        (item.variantCount ?: 0) > 1 || item.durationSeconds == null ||
        renditionQualityKey(item) == null) return null
    val page = canonicalPage(item.pageUrl) ?: return null
    val media = canonicalRenditionUrl(item.url) ?: return null
    return "$page\u0000$media"
}

private fun canonicalPage(value: String): String? = runCatching {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return@runCatching null
    val host = uri.host?.lowercase(Locale.US) ?: return@runCatching null
    val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
    val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).sorted().joinToString("&")
    "$scheme://$host$port${uri.path.orEmpty()}${query.takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()}"
}.getOrNull()

private val QUALITY_PATH_TOKEN = Regex(
    "(?i)(^|[/_.-])(2160p?|1440p?|1080p?|720p?|576p?|480p?|360p?|240p?|uhd|fhd|hd|sd|low|medium|high)(?=$|[/_.-])"
)
private val QUALITY_QUERY_KEYS = setOf("quality", "q", "height", "resolution", "res", "bitrate", "br", "level")
private val TRANSIENT_QUERY_KEYS = setOf("token", "sig", "signature", "expires", "expiry", "exp", "auth")

private fun canonicalRenditionUrl(value: String): String? = runCatching {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return@runCatching null
    val host = uri.host?.lowercase(Locale.US) ?: return@runCatching null
    val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
    val path = QUALITY_PATH_TOKEN.replace(uri.path.orEmpty().lowercase(Locale.US)) {
        it.groupValues[1] + "{quality}"
    }
    val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).filterNot { part ->
        part.substringBefore('=').lowercase(Locale.US) in QUALITY_QUERY_KEYS + TRANSIENT_QUERY_KEYS
    }.sorted().joinToString("&")
    "$scheme://$host$port$path${query.takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()}"
}.getOrNull()

private fun samePresentationDuration(first: DetectedMedia, second: DetectedMedia): Boolean {
    val a = first.durationSeconds ?: return false
    val b = second.durationSeconds ?: return false
    return kotlin.math.abs(a - b) <= max(3.0, max(a, b) * 0.01)
}

private fun renditionQualityCount(items: List<DetectedMedia>): Int =
    items.mapNotNull(::renditionQualityKey).distinct().size

private fun renditionQualityKey(item: DetectedMedia): String? =
    resolutionHeight(item.resolution)?.let { "h:$it" }
        ?: item.bitrateBitsPerSecond?.takeIf { it > 0L }?.let { "b:$it" }

internal fun resolutionHeight(value: String?): Int? {
    val match = Regex("^(\\d+)[xX](\\d+)$").matchEntire(value?.trim().orEmpty()) ?: return null
    val width = match.groupValues[1].toIntOrNull() ?: return null
    val height = match.groupValues[2].toIntOrNull() ?: return null
    return minOf(width, height).takeIf { it > 0 }
}
