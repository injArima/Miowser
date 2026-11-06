package com.miowser.app.browser

import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal data class AnikotoPlaybackSession(
    val tabId: Long,
    val showUrl: String,
    val showTitle: String,
    val episodes: List<AnikotoEpisode>,
    val currentEpisodeUrl: String,
    val language: String,
    val serverId: String?,
    val serverLabel: String?
)

internal sealed interface AnikotoSwitchResult {
    data class Ready(val media: DetectedMedia, val server: AnikotoServer,
        val catalog: AnikotoCatalog) : AnikotoSwitchResult
    data class Failed(val message: String) : AnikotoSwitchResult
}

/** Drives the site's own episode and server controls while the rich player stays on screen. */
internal suspend fun resolveAnikotoSwitch(
    tab: Tab,
    episodeUrl: String,
    language: String,
    preferredServerId: String?
): AnikotoSwitchResult {
    if (!AnikotoAdapter.accepts(episodeUrl) || language !in setOf("sub", "dub"))
        return AnikotoSwitchResult.Failed("This episode choice is no longer valid.")
    val changedEpisode = tab.url != episodeUrl
    if (changedEpisode) tab.navigateTo(episodeUrl)
    tab.liveWebView?.onResume()
    val catalog = withTimeoutOrNull(20_000L) {
        while (true) {
            val current = tab.anikotoCatalog
            if (current != null && current.episodes.any { it.url == episodeUrl } &&
                current.servers.isNotEmpty()) return@withTimeoutOrNull current
            tab.refreshAnikotoCatalog()
            delay(400L)
        }
        @Suppress("UNREACHABLE_CODE") null
    } ?: return AnikotoSwitchResult.Failed("Anikoto did not load this episode's servers. Retry or open the source page.")
    val choices = catalog.servers.filter { it.language == language }
    if (choices.isEmpty()) return AnikotoSwitchResult.Failed(
        "No ${language.uppercase()} server is available for this episode.")
    val ordered = choices.sortedWith(compareByDescending<AnikotoServer> { it.id == preferredServerId })
    for (server in ordered) {
        if (!changedEpisode && server.active) {
            val alreadyPlaying = tab.detectedMedia.firstOrNull { item ->
                item.isConfirmed && item.type in setOf(MediaType.HLS, MediaType.DASH,
                    MediaType.MP4, MediaType.WEBM, MediaType.MATROSKA) &&
                    item.url.startsWith("http")
            }
            if (alreadyPlaying != null) {
                tab.pausePageMedia()
                return AnikotoSwitchResult.Ready(alreadyPlaying, server, catalog)
            }
        }
        val oldUrls = if (changedEpisode) emptySet() else tab.detectedMedia.mapTo(mutableSetOf()) { it.url }
        val clicked = suspendCancellableCoroutine { continuation ->
            tab.selectAnikotoServer(server) { ok ->
                if (continuation.isActive) continuation.resume(ok)
            }
        }
        if (!clicked) continue
        val media = withTimeoutOrNull(9_000L) {
            var playAttempted = false
            var elapsed = 0L
            while (true) {
                val candidate = tab.detectedMedia.firstOrNull { item ->
                    item.isConfirmed && item.type in setOf(MediaType.HLS, MediaType.DASH,
                        MediaType.MP4, MediaType.WEBM, MediaType.MATROSKA) &&
                        item.url.startsWith("http") && item.url !in oldUrls
                }
                if (candidate != null) return@withTimeoutOrNull candidate
                if (!playAttempted && elapsed >= 3_000L) {
                    playAttempted = true
                    tab.liveWebView?.evaluateJavascript(
                        "document.querySelector('#player .play')?.click();", null)
                }
                delay(350L)
                elapsed += 350L
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        if (media != null) {
            tab.pausePageMedia()
            return AnikotoSwitchResult.Ready(media, server, catalog)
        }
    }
    tab.pausePageMedia()
    return AnikotoSwitchResult.Failed(
        "No playable stream was found on the available servers. Retry or open the source page.")
}
