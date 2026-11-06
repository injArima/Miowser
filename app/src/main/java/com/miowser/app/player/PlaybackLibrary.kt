package com.miowser.app.player

import android.net.Uri
import com.miowser.app.grabber.MediaType
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.toLibrarySubtitle

/** Converts a successful player handoff into the persistent shape used by the Hub Library. */
internal fun PlaybackRequest.toLibraryEntry(
    playedAt: Long = System.currentTimeMillis(),
    durationMs: Long = 0L
): LibraryEntry = when (this) {
    is PlaybackRequest.Remote -> LibraryEntry(
        explicitlySaved = false,
        mediaUri = url,
        pageUrl = pageUrl,
        title = title,
        sourceLabel = sourceLabel,
        sourceKind = LibrarySourceKind.REMOTE_STREAM,
        mediaType = mediaType,
        userAgent = userAgent,
        requestReferer = requestReferer,
        requestOrigin = requestOrigin,
        thumbnailUrl = null,
        lastPlayedAt = playedAt,
        positionMs = startPositionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        pageTitle = pageTitle,
        videoName = videoName,
        selectedSubtitle = externalSubtitles.firstOrNull { it.url == preferredSubtitleUrl }?.toLibrarySubtitle(),
        hlsRenditions = hlsRenditions
    )

    is PlaybackRequest.Local -> LibraryEntry(
        explicitlySaved = false,
        mediaUri = uri.toString(),
        pageUrl = pageUrl,
        title = title,
        sourceLabel = sourceLabel,
        sourceKind = LibrarySourceKind.LOCAL_FILE,
        mediaType = null,
        userAgent = null,
        thumbnailUrl = null,
        lastPlayedAt = playedAt,
        positionMs = startPositionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        pageTitle = pageTitle,
        videoName = videoName
    )
}

/** Rebuilds a player request from a persisted card using fresh session cookies. */
internal fun LibraryEntry.toPlaybackRequest(
    freshCookies: String?,
    fallbackUserAgent: String
): PlaybackRequest = when (sourceKind) {
    LibrarySourceKind.REMOTE_STREAM -> PlaybackRequest.Remote(
        url = mediaUri,
        pageUrl = pageUrl ?: mediaUri,
        userAgent = userAgent ?: fallbackUserAgent,
        requestReferer = requestReferer,
        requestOrigin = requestOrigin,
        cookies = freshCookies,
        mediaType = mediaType ?: MediaType.UNKNOWN,
        title = title,
        sourceLabel = sourceLabel,
        pageTitle = pageTitle,
        videoName = videoName,
        posterUrl = null,
        posterCookies = null,
        startPositionMs = libraryResumePosition(positionMs, durationMs),
        externalSubtitles = selectedSubtitle?.let { listOf(it.toDetectedMedia(pageTitle)) }.orEmpty(),
        preferredSubtitleUrl = selectedSubtitle?.url,
        hlsRenditions = hlsRenditions
    )

    LibrarySourceKind.LOCAL_FILE -> PlaybackRequest.Local(
        uri = Uri.parse(mediaUri),
        title = title,
        sourceLabel = sourceLabel,
        pageUrl = pageUrl,
        pageTitle = pageTitle,
        videoName = videoName,
        startPositionMs = libraryResumePosition(positionMs, durationMs)
    )
}

/** Restarts effectively-finished media; otherwise resumes at the exact saved point. */
internal fun libraryResumePosition(positionMs: Long, durationMs: Long): Long {
    val position = positionMs.coerceAtLeast(0L)
    if (durationMs <= 0L) return position
    val duration = durationMs.coerceAtLeast(1L)
    val boundedPosition = position.coerceAtMost(duration)
    val completionWindow = minOf(30_000L, duration / 20L)
    return if (duration - boundedPosition <= completionWindow) 0L else boundedPosition
}
