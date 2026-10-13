package com.miowser.app.downloader

enum class DownloadConflictReason { SAME_SOURCE, SAME_NAME }

data class DownloadConflict(val existing: Download, val reason: DownloadConflictReason)

fun findDownloadConflict(existing: List<Download>, candidate: Download): DownloadConflict? {
    // Always evaluate URL identity first. A matching name must never hide a matching source and
    // reduce the dialog to rename/cancel — source duplication has its own refresh/replace choices.
    val sourceMatch = existing
        .filter { sameDownloadSource(it.sourceUrl, candidate.sourceUrl) }
        .minByOrNull { sourceConflictPriority(it.status) }
    if (sourceMatch != null) return DownloadConflict(sourceMatch, DownloadConflictReason.SAME_SOURCE)

    val candidateNames = comparableDownloadNames(candidate)
    val nameMatch = existing
        .filter { download ->
            download.status.reservesOutputName() &&
            comparableDownloadNames(download).any(candidateNames::contains)
        }
        .minByOrNull { sourceConflictPriority(it.status) }
    if (nameMatch != null) return DownloadConflict(nameMatch, DownloadConflictReason.SAME_NAME)

    return null
}

/** Creates the first unused "name (2).ext" candidate and preserves that basename at save time. */
fun renamedDownloadCopy(candidate: Download, existing: List<Download>): Download {
    val usedNames = existing.map { it.displayName.trim().lowercase() }.toSet()
    val extension = candidate.displayName.substringAfterLast('.', "")
        .takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
        ?: candidate.suggestedExtension
    val originalBase = candidate.outputBaseName.trim().ifBlank { "download" }
    var suffix = 2
    while (true) {
        val renamedBase = "$originalBase ($suffix)"
        val renamedName = extension?.let { "$renamedBase.$it" } ?: renamedBase
        if (renamedName.lowercase() !in usedNames) {
            return candidate.copyForQueue(
                displayName = renamedName,
                outputBaseName = renamedBase,
                preferredOutputBaseName = renamedBase
            )
        }
        suffix++
    }
}

fun Download.copyForQueue(
    id: Long = this.id,
    displayName: String = this.displayName,
    outputBaseName: String = this.outputBaseName,
    preferredOutputBaseName: String? = this.preferredOutputBaseName
): Download = Download(
    id = id,
    sourceUrl = sourceUrl,
    pageUrl = pageUrl,
    userAgent = userAgent,
    referer = referer,
    cookies = cookies,
    kind = kind,
    displayName = displayName,
    outputBaseName = outputBaseName,
    suggestedExtension = suggestedExtension,
    preferredOutputBaseName = preferredOutputBaseName
).also { copy -> copy.rendererTransfer = rendererTransfer }

private fun DownloadStatus.isActivelyDownloading(): Boolean =
    this == DownloadStatus.QUEUED || this == DownloadStatus.DOWNLOADING ||
        this == DownloadStatus.PAUSED || this == DownloadStatus.MUXING

private fun sourceConflictPriority(status: DownloadStatus): Int = when {
    status.isActivelyDownloading() -> 0
    status == DownloadStatus.COMPLETED -> 1
    else -> 2
}

private fun DownloadStatus.reservesOutputName(): Boolean =
    isActivelyDownloading() || this == DownloadStatus.COMPLETED

/** Scheme/host are case-insensitive; path/query are not. URL fragments never change the file. */
internal fun sameDownloadSource(first: String, second: String): Boolean {
    fun normalized(value: String): SourceIdentity? = runCatching {
        val uri = java.net.URI(value.trim())
        if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return@runCatching null
        SourceIdentity(
            scheme = uri.scheme.lowercase(),
            host = uri.host.lowercase(),
            port = when {
                uri.port >= 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                uri.scheme.equals("http", ignoreCase = true) -> 80
                else -> -1
            },
            path = uri.rawPath.orEmpty().ifBlank { "/" },
            query = uri.rawQuery
        )
    }.getOrNull()

    val firstIdentity = normalized(first)
    val secondIdentity = normalized(second)
    return if (firstIdentity != null && secondIdentity != null) {
        firstIdentity == secondIdentity
    } else {
        first.trim() == second.trim()
    }
}

private data class SourceIdentity(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: String?
)

private fun comparableDownloadNames(download: Download): Set<String> = buildSet {
    download.displayName.trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
    download.outputBaseName.trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
    download.displayName.substringBeforeLast('.', download.displayName)
        .trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
}
