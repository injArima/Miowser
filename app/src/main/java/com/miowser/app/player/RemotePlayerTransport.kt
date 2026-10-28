package com.miowser.app.player

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.miowser.app.grabber.MediaType

/** Keeps manifests fresh; only the actual media bytes pass through the short-lived disk cache. */
@OptIn(UnstableApi::class)
internal class RemotePlayerTransport private constructor(
    val mediaSourceFactory: MediaSource.Factory,
    val prefetcher: PlayerWindowPrefetcher?,
    private val cacheSession: PlayerCacheSession?
) {
    fun close() { cacheSession?.close() }

    companion object {
        fun create(context: Context, request: PlaybackRequest.Remote, preparedSession: PlayerCacheSession?): RemotePlayerTransport {
            val http = DefaultHttpDataSource.Factory().setUserAgent(request.userAgent)
            val subtitlesByUrl = request.externalSubtitles.associateBy { it.url }
            val headersFor: (String) -> Map<String, String> = { url ->
                val subtitle = subtitlesByUrl[url]
                val referer = mediaReferer(
                    subtitle?.pageUrl ?: request.pageUrl, url,
                    subtitle?.requestReferer ?: if (subtitle == null) request.requestReferer else null
                )
                val origin = subtitle?.requestOrigin ?: if (subtitle == null) request.requestOrigin else null
                val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                    ?: request.cookies.takeIf {
                        subtitle == null && Uri.parse(url).host == Uri.parse(request.url).host
                    }
                buildMap {
                    referer?.let { put("Referer", it) }
                    origin?.let { put("Origin", it) }
                    cookies?.let { put("Cookie", it) }
                }
            }
            val resolver = ResolvingDataSource.Resolver { spec: DataSpec ->
                spec.withRequestHeaders(spec.httpRequestHeaders + headersFor(spec.uri.toString()))
            }
            val playbackHttp = androidx.media3.datasource.DataSource.Factory {
                BudgetedDataSource(http.createDataSource(), com.miowser.app.network.ConnectionBudget.Priority.PLAYBACK)
            }
            var prefetcher: PlayerWindowPrefetcher? = null
            val prefetchHttp = androidx.media3.datasource.DataSource.Factory {
                BudgetedDataSource(http.createDataSource(), com.miowser.app.network.ConnectionBudget.Priority.PREFETCH) { prefetcher?.checkActive() }
            }
            val uncached = ResolvingDataSource.Factory(playbackHttp, resolver)
            val prefetchUncached = ResolvingDataSource.Factory(prefetchHttp, resolver)
            // Synthetic HLS masters use a data: URI. DefaultDataSource handles that locally and
            // delegates each absolute HTTP leaf playlist to the authenticated resolver above.
            val manifestUncached = DefaultDataSource.Factory(context, uncached)
            if (Uri.parse(request.url).scheme?.lowercase() !in setOf("http", "https")) {
                return RemotePlayerTransport(DefaultMediaSourceFactory(uncached), null, null)
            }
            val type = when {
                request.mediaType == MediaType.HLS || request.url.substringBefore('?').endsWith(".m3u8", true) -> MediaType.HLS
                request.mediaType == MediaType.DASH || request.url.substringBefore('?').endsWith(".mpd", true) -> MediaType.DASH
                else -> request.mediaType
            }
            val session = preparedSession
            if (session == null) {
                val sourceFactory: MediaSource.Factory = when (type) {
                    MediaType.HLS -> HlsMediaSource.Factory(HlsDataSourceFactory { dataType ->
                        if (dataType == C.DATA_TYPE_MEDIA) uncached.createDataSource()
                        else manifestUncached.createDataSource()
                    })
                    MediaType.DASH -> DashMediaSource.Factory(
                        DefaultDashChunkSource.Factory(uncached), uncached)
                    else -> DefaultMediaSourceFactory(uncached)
                }
                return RemotePlayerTransport(sourceFactory, null, null)
            }
            val cacheFactory = session.factory(playbackHttp)
            val prefetchCacheFactory = session.factory(prefetchHttp)
            val cached = ResolvingDataSource.Factory(cacheFactory, resolver)
            val sourceFactory: MediaSource.Factory = when (type) {
                MediaType.HLS -> HlsMediaSource.Factory(HlsDataSourceFactory { dataType ->
                    if (dataType == C.DATA_TYPE_MEDIA) cached.createDataSource()
                    else manifestUncached.createDataSource()
                })
                MediaType.DASH -> DashMediaSource.Factory(
                    DefaultDashChunkSource.Factory(cached), uncached)
                else -> DefaultMediaSourceFactory(cached)
            }
            if (request.hlsRenditions.size < 2) {
                prefetcher = PlayerWindowPrefetcher(request.url, type, prefetchUncached,
                    prefetchCacheFactory, session, headersFor)
            }
            return RemotePlayerTransport(sourceFactory, prefetcher, session)
        }
    }
}
