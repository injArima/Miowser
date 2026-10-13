package com.miowser.app.grabber

import java.io.InputStream

internal enum class HeadProbeAction { ACCEPT, SAMPLE_WITH_GET, REJECT }

internal fun decideHeadProbe(statusCode: Int, contentType: String?, guessedType: MediaType): HeadProbeAction {
    if (statusCode == 404 || statusCode == 410) return HeadProbeAction.REJECT
    if (statusCode !in 200..299) return HeadProbeAction.SAMPLE_WITH_GET

    val normalized = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (normalized.isNullOrBlank() || normalized in genericBinaryMimeTypes) {
        return HeadProbeAction.SAMPLE_WITH_GET
    }
    val declaredType = classifyProbeMimeType(normalized, guessedType)
        ?: return HeadProbeAction.REJECT
    return if (mediaTypesCompatible(guessedType, declaredType)) {
        HeadProbeAction.ACCEPT
    } else {
        HeadProbeAction.REJECT
    }
}

internal fun resolveSampledMediaType(
    guessedType: MediaType,
    contentType: String?,
    signatureExtension: String?
): MediaType? {
    val normalized = contentType?.substringBefore(';')?.trim()?.lowercase()
    val declaredType = normalized
        ?.takeUnless { it in genericBinaryMimeTypes }
        ?.let { classifyProbeMimeType(it, guessedType) }
    if (normalized != null && normalized !in genericBinaryMimeTypes && declaredType == null) return null
    if (declaredType != null && !mediaTypesCompatible(guessedType, declaredType)) return null

    val signatureType = signatureExtension?.let { extension ->
        MediaUrlClassifier.classify("https://probe.invalid/file.$extension")
    }
    if (signatureType != null && !mediaTypesCompatible(guessedType, signatureType)) return null
    return signatureType ?: declaredType
}

internal fun exactProbeSize(
    statusCode: Int,
    isHead: Boolean,
    contentRange: String?,
    contentLength: Long
): Long? {
    if (statusCode !in 200..299) return null
    parseProbeContentRange(contentRange)?.total?.let { return it }
    // A 206 Content-Length is only the sampled range. HEAD and a range-ignoring 200 response
    // describe the complete representation and are safe to expose as exact.
    return contentLength.takeIf { it > 0L && (isHead || statusCode == 200) }
}

internal data class ProbeContentRange(val start: Long, val endInclusive: Long, val total: Long?)

internal fun parseProbeContentRange(value: String?): ProbeContentRange? {
    if (value == null) return null
    val match = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    if (end < start) return null
    val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
    if (total != null && (total <= 0L || end >= total)) return null
    return ProbeContentRange(start, end, total)
}

internal fun parseProbeContentRangeTotal(value: String?): Long? = parseProbeContentRange(value)?.total

internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArray(maxBytes)
    var total = 0
    while (total < maxBytes) {
        val read = read(output, total, maxBytes - total)
        if (read == -1) break
        if (read == 0) continue
        total += read
    }
    return output.copyOf(total)
}

internal data class ProbeSizeEstimate(val bytes: Long?, val isApproximate: Boolean)

internal fun estimateManifestSize(
    totalDurationSeconds: Double?,
    declaredSizeBytes: Long?,
    bitrateBitsPerSecond: Long?,
    sampledSegments: List<Pair<Long, Double>>
): ProbeSizeEstimate {
    declaredSizeBytes?.takeIf { it >= 0L }?.let { return ProbeSizeEstimate(it, false) }
    val totalDuration = totalDurationSeconds?.takeIf { it.isFinite() && it > 0.0 }
        ?: return ProbeSizeEstimate(null, false)
    val validSamples = sampledSegments.filter { (bytes, seconds) ->
        bytes > 0L && seconds.isFinite() && seconds > 0.0
    }
    if (validSamples.isNotEmpty()) {
        val sampledBytes = validSamples.sumOf { it.first.toDouble() }
        val sampledSeconds = validSamples.sumOf { it.second }
        return ProbeSizeEstimate((sampledBytes / sampledSeconds * totalDuration * 1.05).toLong(), true)
    }
    val bitrate = bitrateBitsPerSecond?.takeIf { it > 0L }
        ?: return ProbeSizeEstimate(null, false)
    return ProbeSizeEstimate((bitrate.toDouble() / 8.0 * totalDuration * 1.03).toLong(), true)
}

private val genericBinaryMimeTypes = setOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/force-download",
    "application/download",
    "application/x-download"
)

internal fun isInconclusiveProbeMimeType(value: String?): Boolean {
    val normalized = value?.substringBefore(';')?.trim()?.lowercase()
    return normalized.isNullOrBlank() || normalized in genericBinaryMimeTypes
}

private fun classifyProbeMimeType(contentType: String, guessedType: MediaType): MediaType? {
    if (guessedType == MediaType.SUBTITLE && contentType in subtitleTextMimeTypes) {
        return MediaType.SUBTITLE
    }
    return MediaUrlClassifier.classifyByMimeType(contentType)
}

private val subtitleTextMimeTypes = setOf("text/plain", "text/x-ssa", "text/x-ass")

private fun mediaTypesCompatible(guessed: MediaType, actual: MediaType): Boolean = when (guessed) {
    MediaType.MP4 -> actual == MediaType.MP4
    MediaType.WEBM -> actual == MediaType.WEBM
    MediaType.MATROSKA -> actual == MediaType.MATROSKA
    MediaType.AUDIO -> actual == MediaType.AUDIO
    MediaType.SUBTITLE -> actual == MediaType.SUBTITLE
    MediaType.UNKNOWN -> actual !in setOf(MediaType.HLS, MediaType.DASH)
    MediaType.HLS, MediaType.DASH -> guessed == actual
}
