package com.miowser.app.downloader

enum class DownloadedFileType { MEDIA, APK, OTHER }

private val playableExtensions = setOf(
    "mp4", "m4v", "mov", "webm", "mkv", "avi", "mpeg", "mpg", "ts",
    "mp3", "m4a", "aac", "ogg", "oga", "flac", "wav", "opus"
)

fun downloadedFileType(fileName: String): DownloadedFileType {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension == "apk" -> DownloadedFileType.APK
        extension in playableExtensions -> DownloadedFileType.MEDIA
        else -> DownloadedFileType.OTHER
    }
}

/** Content evidence outranks a generic or misleading saved filename. */
fun downloadedFileType(
    fileName: String,
    mimeType: String?,
    signatureExtension: String?,
    kind: DownloadKind,
    sourceUrl: String
): DownloadedFileType {
    val signature = signatureExtension?.lowercase()
    if (signature in playableExtensions) return DownloadedFileType.MEDIA
    if (signature == "apk") return DownloadedFileType.APK
    if (signature == "zip" && downloadedFileType(fileName) == DownloadedFileType.APK) {
        // An APK is a ZIP; its manifest may lie beyond the bounded signature prefix.
        return DownloadedFileType.APK
    }
    if (signature in setOf("pdf", "zip", "png", "jpg")) return DownloadedFileType.OTHER
    if (kind == DownloadKind.HLS || kind == DownloadKind.DASH) return DownloadedFileType.MEDIA
    if (downloadedFileType(fileName) == DownloadedFileType.APK) return DownloadedFileType.APK
    val declared = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (declared.startsWith("video/") || declared.startsWith("audio/")) return DownloadedFileType.MEDIA
    if (downloadedFileType(fileName) == DownloadedFileType.MEDIA ||
        downloadedFileType(sourceUrl.substringBefore('?').substringBefore('#')) == DownloadedFileType.MEDIA
    ) return DownloadedFileType.MEDIA
    return DownloadedFileType.OTHER
}

fun mimeTypeForDownloadedFile(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "apk" -> "application/vnd.android.package-archive"
    "pdf" -> "application/pdf"
    "zip" -> "application/zip"
    "mp4", "m4v" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "mpeg", "mpg" -> "video/mpeg"
    "ts" -> "video/mp2t"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg", "oga" -> "audio/ogg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "opus" -> "audio/opus"
    "vtt" -> "text/vtt"
    "srt" -> "application/x-subrip"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "txt", "log" -> "text/plain"
    else -> "application/octet-stream"
}
