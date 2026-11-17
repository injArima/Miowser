package com.miowser.app.browser.media

/** Website Media Session action names; commands stay in the owning page's engine. */
internal interface PageMediaControls {
    val snapshot: PageMediaSnapshot?
    fun command(action: String, positionMs: Long? = null)
}

internal data class PageMediaSnapshot(
    val tabId: Long,
    val ownerKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val pageUrl: String,
    val playing: Boolean,
    val buffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val actions: Set<String>,
    val isPrivate: Boolean = false
) {
    internal fun hasSameOwner(other: PageMediaSnapshot): Boolean = tabId == other.tabId &&
        ownerKey == other.ownerKey && pageUrl == other.pageUrl && isPrivate == other.isPrivate

    /** Never pass private site metadata (including its URL) to Android or the artwork loader. */
    internal fun forPublication(): PageMediaSnapshot = copy(
        title = if (isPrivate) "Private media" else title.ifBlank { "Browser media" }.take(512),
        artist = if (isPrivate) "" else artist.take(512),
        album = if (isPrivate) "" else album.take(512),
        artworkUrl = if (isPrivate) null else artworkUrl,
        pageUrl = if (isPrivate) "" else pageUrl.take(4096),
        positionMs = positionMs.coerceAtLeast(0).let {
            if (durationMs > 0) it.coerceAtMost(durationMs) else it
        },
        durationMs = durationMs.coerceAtLeast(-1),
        speed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f,
        actions = actions.intersect(PageMediaActions.supported)
    )

    internal fun notificationKey() = PageMediaNotificationKey(
        tabId, ownerKey, title, artist, album, artworkUrl, pageUrl, playing, actions, isPrivate
    )
}

internal object PageMediaActions {
    val supported = setOf("play", "pause", "stop", "seekto", "seekbackward", "seekforward",
        "previoustrack", "nexttrack")
}

/** Position, duration, buffering and rate samples don't require rebuilding a notification. */
internal data class PageMediaNotificationKey(
    val tabId: Long, val ownerKey: String, val title: String, val artist: String,
    val album: String, val artworkUrl: String?, val pageUrl: String, val playing: Boolean,
    val actions: Set<String>, val isPrivate: Boolean
)

/** Pure validation shared by the exported-activity entry point and model tests. */
internal data class PageMediaNotificationAccess(val snapshot: PageMediaSnapshot, val token: String) {
    fun tabId(requestToken: String?, requestTabId: Long, current: PageMediaSnapshot?): Long? =
        requestTabId.takeIf {
            token.isNotBlank() && requestToken == token && requestTabId == snapshot.tabId &&
                current != null && snapshot.hasSameOwner(current)
        }
}

/** Anchor passed to PlaybackState, whose monotonic clock extrapolates between page samples. */
internal data class PageMediaPositionAnchor(val snapshot: PageMediaSnapshot, val elapsedMs: Long) {
    fun positionAt(nowMs: Long): Long {
        val advance = if (snapshot.playing && !snapshot.buffering) {
            (nowMs - elapsedMs).coerceAtLeast(0).toDouble() * snapshot.speed
        } else 0.0
        val position = (snapshot.positionMs.toDouble() + advance).toLong().coerceAtLeast(0)
        return if (snapshot.durationMs > 0) position.coerceAtMost(snapshot.durationMs) else position
    }

    fun needsUpdate(next: PageMediaSnapshot, nowMs: Long): Boolean =
        !snapshot.hasSameOwner(next) ||
            snapshot.playing != next.playing || snapshot.buffering != next.buffering ||
            snapshot.durationMs != next.durationMs || snapshot.speed != next.speed ||
            snapshot.actions != next.actions ||
            if (next.playing && !next.buffering) {
                kotlin.math.abs(positionAt(nowMs) - next.positionMs) > 1_500L
            } else positionAt(nowMs) != next.positionMs
}
