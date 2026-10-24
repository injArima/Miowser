package com.miowser.app.browser

import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import kotlin.math.abs

/** Other verified media seen by this tab, offered as choices rather than assumed to back a blob. */
internal fun verifiedSourcesForBlob(
    blob: DetectedMedia,
    detections: List<DetectedMedia>
): List<DetectedMedia> = detections.asSequence()
    .filter { candidate ->
        candidate.url != blob.url && candidate.isConfirmed && candidate.finalUrl != null &&
            rendererResourceKind(candidate.url) == null &&
            candidate.type in setOf(
                MediaType.HLS, MediaType.DASH, MediaType.MP4,
                MediaType.WEBM, MediaType.MATROSKA
            )
    }
    .sortedBy { candidate -> abs(candidate.detectedAt - blob.detectedAt) }
    .take(24)
    .toList()
