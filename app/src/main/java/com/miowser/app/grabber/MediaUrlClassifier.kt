package com.miowser.app.grabber

/**
 * First-pass, cheap classification of a request URL by extension — enough to decide whether a
 * URL is worth an actual verification probe. Not the final answer on its own: a server's
 * declared Content-Type and a URL's extension both lie often enough in practice (validated
 * against 1DM's own grabber — see project memory: 1dm-grabber-mechanics.md) that manifests get
 * their body sniffed for real magic bytes by [ManifestSniffer]/[MediaProbe] before being
 * reported as detected.
 */
object MediaUrlClassifier {
    // HLS/DASH segments, key files, and init segments — never top-level media in their own
    // right, so excluded even though some (.ts) share an extension family with real media.
    private val EXCLUDED_EXTENSIONS = setOf("ts", "m4s", "key")

    private val EXTENSION_TYPES = mapOf(
        "m3u8" to MediaType.HLS,
        "mpd" to MediaType.DASH,
        "mp4" to MediaType.MP4,
        "m4v" to MediaType.MP4,
        "mov" to MediaType.MP4,
        "webm" to MediaType.WEBM,
        "mkv" to MediaType.MATROSKA,
        "mp3" to MediaType.AUDIO,
        "m4a" to MediaType.AUDIO,
        "aac" to MediaType.AUDIO,
        "flac" to MediaType.AUDIO,
        "ogg" to MediaType.AUDIO,
        "oga" to MediaType.AUDIO,
        "wav" to MediaType.AUDIO,
        "opus" to MediaType.AUDIO,
        "vtt" to MediaType.SUBTITLE,
        "srt" to MediaType.SUBTITLE,
        "ass" to MediaType.SUBTITLE
    )

    /** Returns null if this URL isn't worth probing at all (a segment, or no recognizable extension). */
    fun classify(url: String): MediaType? {
        val path = url.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension.isEmpty() || extension in EXCLUDED_EXTENSIONS) return null
        return EXTENSION_TYPES[extension]
    }

    fun isManifestType(type: MediaType): Boolean = type == MediaType.HLS || type == MediaType.DASH
}
