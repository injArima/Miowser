package com.miowser.app.downloader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStatus { QUEUED, DOWNLOADING, MUXING, COMPLETED, FAILED, CANCELED }

enum class DownloadKind { DIRECT_FILE, HLS, DASH }

/**
 * One user-visible download. Progress fields are Compose-observable properties on a plain class
 * (like Tab), not a data class replaced wholesale in a list on every update — a download reports
 * progress many times a second, and re-inserting into a mutableStateListOf that often would
 * fight Compose's diffing for no benefit.
 *
 * [bytesDownloaded]/[totalBytes] mean actual bytes for a direct-file download, but *segments
 * completed/total* for HLS and DASH — neither's real byte total is known upfront (see
 * MediaProbe's own HLS size estimate, which is a separate, approximate number). Either way the
 * ratio is a valid 0..1 progress fraction, which is all the UI actually needs.
 */
class Download(
    val id: Long,
    val sourceUrl: String,
    val pageUrl: String,
    val userAgent: String,
    val referer: String,
    val cookies: String?,
    val kind: DownloadKind,
    val displayName: String,
    val outputBaseName: String
) {
    var status by mutableStateOf(DownloadStatus.QUEUED)
    var bytesDownloaded by mutableStateOf(0L)
    var totalBytes by mutableStateOf(-1L) // -1 = unknown
    var errorMessage by mutableStateOf<String?>(null)

    // Checked from the download thread to stop early; not Compose state, and deliberately not
    // read by the UI (status transitioning to CANCELED is what the UI reacts to).
    @Volatile var isCanceled = false

    companion object {
        private val idCounter = AtomicLong(0)
        fun nextId(): Long = idCounter.getAndIncrement()

        /** Bumps the counter past a restored download's old id, so new downloads never collide with it. */
        fun ensureNextIdAbove(id: Long) {
            idCounter.updateAndGet { current -> maxOf(current, id + 1) }
        }
    }
}
