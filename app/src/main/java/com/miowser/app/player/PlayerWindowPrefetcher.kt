package com.miowser.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.miowser.app.grabber.MediaType
import java.io.IOException
import kotlin.math.abs

private const val DIRECT_CHUNK_BYTES = 2L * 1024 * 1024
private const val MAX_SEGMENTS_PER_WINDOW = 600

internal data class PlayerStreamSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val bufferedAheadMs: Long,
    val videoHeight: Int,
    val videoBitrate: Int,
    val audioLanguage: String?
)

/** Fetches one bounded, currently relevant cache piece per pass. Errors never affect playback. */
@OptIn(UnstableApi::class)
internal class PlayerWindowPrefetcher(
    private val mediaUrl: String,
    private val mediaType: MediaType,
    private val uncached: DataSource.Factory,
    private val cacheFactory: CacheDataSource.Factory,
    private val cacheSession: PlayerCacheSession,
    private val headersFor: (String) -> Map<String, String>
) {
    private data class TimedSpec(val timeMs: Long, val spec: DataSpec)

    @Volatile private var activeSource: DataSource? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private var passGeneration = 0L
    fun cancel() {
        generation.incrementAndGet()
        cacheSession.cancelPrefetch()
        activeSource?.let { source -> cleanup.execute { runCatching { source.close() } } }
    }
    internal fun checkActive() {
        if (passGeneration != generation.get()) throw java.util.concurrent.CancellationException()
    }
    companion object {
        private val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor {
            Thread(it, "Miowser-prefetch-close")
        }
    }
    private var candidateKey: String? = null
    private var retainedCandidates: List<TimedSpec> = emptyList()

    private var failedUntil = 0L
    private var rootHls: HlsPlaylist? = null
    private var selectedHlsKey: String? = null
    private var selectedHls: List<HlsMediaPlaylist>? = null
    private var dash: DashManifest? = null
    private var directLength: Long? = null
    private var directRangeChecked = false
    private var directCursor = -1L
    private var lastPositionMs = -1L

    fun pass(snapshot: PlayerStreamSnapshot, connection: PlayerConnection): Boolean {
        if (connection == PlayerConnection.OFFLINE || snapshot.durationMs <= 0L ||
            snapshot.bufferedAheadMs < 15_000L || System.currentTimeMillis() < failedUntil) return false
        passGeneration = generation.get()
        val window = playerBufferWindow(connection)
        return try {
            when {
                mediaType == MediaType.HLS || mediaUrl.substringBefore('?').endsWith(".m3u8", true) ->
                    prefetchHls(snapshot, window)
                mediaType == MediaType.DASH || mediaUrl.substringBefore('?').endsWith(".mpd", true) ->
                    prefetchDash(snapshot, window)
                else -> prefetchProgressive(snapshot, window)
            }
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException || passGeneration != generation.get()) return false
            if (error is InterruptedException) Thread.currentThread().interrupt()
            failedUntil = System.currentTimeMillis() + 30_000L
            false
        }
    }

    private fun prefetchHls(snapshot: PlayerStreamSnapshot, window: PlayerBufferWindow): Boolean {
        val root = rootHls ?: loadHls(Uri.parse(mediaUrl)).also { rootHls = it }
        val playlists = when (root) {
            is HlsMediaPlaylist -> listOf(root)
            is HlsMultivariantPlaylist -> {
                if (snapshot.videoHeight <= 0 && snapshot.videoBitrate <= 0) return false
                val variant = root.variants.minByOrNull { variant ->
                    val heightDelta = if (snapshot.videoHeight > 0 && variant.format.height > 0)
                        abs(variant.format.height - snapshot.videoHeight) * 100_000L else 0L
                    heightDelta + if (snapshot.videoBitrate > 0 && variant.format.bitrate > 0)
                        abs(variant.format.bitrate.toLong() - snapshot.videoBitrate) else 0L
                } ?: return false
                val audio = root.audios.firstOrNull { rendition ->
                    rendition.groupId == variant.audioGroupId && rendition.url != null &&
                        rendition.format.language == snapshot.audioLanguage
                } ?: root.audios.firstOrNull {
                    it.groupId == variant.audioGroupId && it.url != null
                }
                val key = variant.url.toString() + "|" + audio?.url
                if (selectedHlsKey != key) {
                    selectedHls = listOfNotNull(variant.url, audio?.url).map(::loadHls)
                        .mapNotNull { it as? HlsMediaPlaylist }
                    selectedHlsKey = key
                }
                selectedHls.orEmpty()
            }
            else -> emptyList()
        }
        if (playlists.isEmpty() || playlists.any { !it.hasEndTag }) return false
        val planKey = "hls:${selectedHlsKey}:${snapshot.positionMs / 10_000}:${window}"
        if (candidateKey == planKey) return cacheNearest(retainedCandidates, snapshot.positionMs)
        val candidates = playlists.flatMap { playlist ->
            playlist.segments.asSequence().filter { segment ->
                !segment.hasGapTag && inWindow(
                    segment.relativeStartTimeUs / 1_000L,
                    segment.durationUs / 1_000L, snapshot.positionMs, window
                )
            }.take(MAX_SEGMENTS_PER_WINDOW).flatMap { segment ->
                listOfNotNull(segment.initializationSegment, segment).asSequence().map { part ->
                    val uri = UriUtil.resolveToUri(playlist.baseUri, part.url)
                    TimedSpec(segment.relativeStartTimeUs / 1_000L,
                        DataSpec.Builder().setUri(uri).setPosition(part.byteRangeOffset)
                            .setLength(part.byteRangeLength).build())
                }
            }.toList()
        }
        candidateKey = planKey
        retainedCandidates = candidates
        return cacheNearest(candidates, snapshot.positionMs)
    }

    private fun prefetchDash(snapshot: PlayerStreamSnapshot, window: PlayerBufferWindow): Boolean {
        val manifest = dash ?: loadDash(Uri.parse(mediaUrl)).also { dash = it }
        if (manifest.dynamic) return false // A live presentation has no future 5/15-minute window.
        val periodIndex = (0 until manifest.periodCount).lastOrNull {
            manifest.getPeriod(it).startMs <= snapshot.positionMs
        } ?: return false
        val period = manifest.getPeriod(periodIndex)
        val periodDurationUs = manifest.getPeriodDurationUs(periodIndex)
        if (periodDurationUs <= 0) return false
        val relativePositionMs = (snapshot.positionMs - period.startMs).coerceAtLeast(0)
        val planKey = "dash:$periodIndex:${snapshot.videoHeight}:${snapshot.videoBitrate}:${snapshot.audioLanguage}:${snapshot.positionMs / 10_000}:$window"
        if (candidateKey == planKey) return cacheNearest(retainedCandidates, snapshot.positionMs)
        val candidates = period.adaptationSets.filter { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { adaptation ->
                val selected = adaptation.representations.minByOrNull { representation ->
                    when (adaptation.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            val heightDelta = if (snapshot.videoHeight > 0 && representation.format.height > 0)
                                abs(representation.format.height - snapshot.videoHeight) * 100_000L else 0L
                            heightDelta + if (snapshot.videoBitrate > 0 && representation.format.bitrate > 0)
                                abs(representation.format.bitrate.toLong() - snapshot.videoBitrate) else 0L
                        }
                        else -> if (representation.format.language == snapshot.audioLanguage) 0L else 1L
                    }
                } ?: return@flatMap emptyList<TimedSpec>()
                val index = selected.index ?: return@flatMap emptyList<TimedSpec>()
                val count = index.getSegmentCount(periodDurationUs)
                if (count <= 0 || count == -1L) return@flatMap emptyList<TimedSpec>()
                val base = selected.baseUrls.firstOrNull()?.url ?: return@flatMap emptyList<TimedSpec>()
                val startMs = (relativePositionMs - window.behindMs).coerceAtLeast(0)
                val endMs = minOf(periodDurationUs / 1_000L, relativePositionMs + window.aheadMs)
                val first = index.getSegmentNum(startMs * 1_000L, periodDurationUs)
                val last = index.getSegmentNum(endMs * 1_000L, periodDurationUs)
                val init = selected.initializationUri?.let {
                    TimedSpec(snapshot.positionMs, rangedSpec(base, it))
                }
                val final = minOf(last, first + MAX_SEGMENTS_PER_WINDOW,
                    index.firstSegmentNum + count - 1)
                val media = if (final < first) emptyList() else (first..final).map { number ->
                    TimedSpec(period.startMs + index.getTimeUs(number) / 1_000L,
                        rangedSpec(base, index.getSegmentUrl(number)))
                }
                listOfNotNull(init) + media
            }
        candidateKey = planKey
        retainedCandidates = candidates
        return cacheNearest(candidates, snapshot.positionMs)
    }

    private fun prefetchProgressive(snapshot: PlayerStreamSnapshot, window: PlayerBufferWindow): Boolean {
        if (!directRangeChecked) {
            val probedLength = probeRangeLength() // Do not prefetch if the server ignores byte ranges.
            checkActive()
            directLength = probedLength
            directRangeChecked = probedLength != null
            if (directLength == null) {
                directRangeChecked = false
                failedUntil = System.currentTimeMillis() + 60_000L
                return false
            }
        }
        val length = directLength ?: return false
        val position = snapshot.positionMs.coerceIn(0, snapshot.durationMs)
        val start = length * (position - window.behindMs).coerceAtLeast(0) / snapshot.durationMs
        val end = length * (position + window.aheadMs).coerceAtMost(snapshot.durationMs) / snapshot.durationMs
        if (lastPositionMs < 0 || abs(position - lastPositionMs) > 30_000L) {
            directCursor = length * position / snapshot.durationMs
        }
        lastPositionMs = position
        val next = if (directCursor in start until end) directCursor else start
        fun pieces(from: Long, until: Long) = generateSequence(from.takeIf { it < until }) { candidate ->
            (candidate + DIRECT_CHUNK_BYTES).takeIf { it < until }
        }
        val chosen = (pieces(next, end) + pieces(start, next)).firstOrNull { candidate ->
            val bytes = minOf(DIRECT_CHUNK_BYTES, end - candidate, length - candidate)
            !cacheSession.isCached(DataSpec.Builder().setUri(mediaUrl).setPosition(candidate).setLength(bytes)
                .setHttpRequestHeaders(headersFor(mediaUrl)).build())
        } ?: return false
        val amount = minOf(DIRECT_CHUNK_BYTES, end - chosen, length - chosen)
        if (amount <= 0) return false
        val spec = DataSpec.Builder().setUri(mediaUrl).setPosition(chosen).setLength(amount)
            .setHttpRequestHeaders(headersFor(mediaUrl)).build()
        checkActive()
        val changed = cacheSession.prefetch(cacheFactory, spec, ::checkActive)
        if (cacheSession.isCached(spec)) directCursor = chosen + amount
        return changed
    }

    private fun probeRangeLength(): Long? {
        val uri = Uri.parse(mediaUrl)
        checkActive()
        val source = uncached.createDataSource()
        activeSource = source
        return try {
            source.open(DataSpec.Builder().setUri(uri).setLength(1).build())
            val header = source.responseHeaders.entries.firstOrNull {
                it.key.equals("Content-Range", true)
            }?.value?.firstOrNull()
            header?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0L }
        } catch (_: IOException) { checkActive(); null }
        finally { activeSource = null; runCatching { source.close() } }
    }

    private fun cacheNearest(candidates: List<TimedSpec>, positionMs: Long): Boolean {
        // Candidate window plans are retained across passes; only the cache spans are rechecked.
        val ordered = candidates.sortedWith(compareBy<TimedSpec> { if (it.timeMs >= positionMs) 0 else 1 }
            .thenBy { abs(it.timeMs - positionMs) })
        for (item in ordered) {
            checkActive()
            val spec = item.spec.buildUpon()
                .setHttpRequestHeaders(headersFor(item.spec.uri.toString())).build()
            if (cacheSession.isCached(spec)) continue
            // Stop at the first missing piece. Previously every pass copied all candidate specs,
            // fetched all cookies and hashed all keys, even when the nearest piece was missing.
            return cacheSession.prefetch(cacheFactory, spec, ::checkActive)
        }
        return false
    }

    private fun rangedSpec(base: String, ranged: RangedUri): DataSpec = DataSpec.Builder()
        .setUri(ranged.resolveUri(base)).setPosition(ranged.start).setLength(ranged.length).build()

    private fun loadHls(uri: Uri): HlsPlaylist = parse(uri) { HlsPlaylistParser().parse(uri, it) }
    private fun loadDash(uri: Uri): DashManifest = parse(uri) { DashManifestParser().parse(uri, it) }

    private fun <T> parse(uri: Uri, parser: (java.io.InputStream) -> T): T {
        checkActive()
        val source = uncached.createDataSource()
        activeSource = source
        return try {
            DataSourceInputStream(source, DataSpec(uri)).use { input ->
                val bounded = object : java.io.FilterInputStream(input) {
                    private var remaining = 4 * 1024 * 1024
                    override fun read(): Int {
                        checkActive()
                        val result = super.read()
                        if (result >= 0) { require(remaining > 0) { "Manifest exceeds its metadata limit" }; remaining-- }
                        return result
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        checkActive()
                        val count = super.read(buffer, offset, minOf(length, remaining + 1))
                        if (count > 0) { require(count <= remaining) { "Manifest exceeds its metadata limit" }; remaining -= count }
                        return count
                    }
                }
                parser(bounded)
            }
        }
        finally { activeSource = null; runCatching { source.close() } }
    }
}

private fun inWindow(startMs: Long, durationMs: Long, positionMs: Long, window: PlayerBufferWindow): Boolean =
    startMs + durationMs > (positionMs - window.behindMs).coerceAtLeast(0) &&
        startMs < positionMs + window.aheadMs
