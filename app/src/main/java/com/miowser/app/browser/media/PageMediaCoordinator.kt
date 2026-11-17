package com.miowser.app.browser.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.miowser.app.browser.video.BrowserPlaybackService

/** One page owns system controls. The WebView remains its only playback engine. Main thread only. */
internal object PageMediaCoordinator {
    private val pages = linkedMapOf<PageMediaTracker, Context>()
    private val observed = mutableMapOf<PageMediaTracker, Pair<String, Boolean>>()
    private var owner: PageMediaTracker? = null
    private var backgroundEnabled = false
    private var foreground = true
    private var suppressed = false
    private var availableTabs: Set<Long>? = null
    private var fullscreenAudioTabId: Long? = null
    private var revision by mutableIntStateOf(0)
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var idleTimeout: Runnable? = null

    fun register(context: Context, tracker: PageMediaTracker) {
        pages[tracker] = context.applicationContext
        changed(tracker)
    }

    fun unregister(tracker: PageMediaTracker) {
        if (owner === tracker) release()
        pages.remove(tracker)
        observed.remove(tracker)
        tracker.setBackgroundAllowed(false)
    }

    fun changed(tracker: PageMediaTracker) {
        changed(tracker, allowExistingPlayback = false)
    }

    private fun changed(tracker: PageMediaTracker, allowExistingPlayback: Boolean) {
        val context = pages[tracker] ?: return
        val snapshot = tracker.snapshot
        if (snapshot == null) {
            observed.remove(tracker)
            if (owner === tracker) release()
            return
        }
        val before = observed.put(tracker, snapshot.ownerKey to snapshot.playing)
        val started = snapshot.playing &&
            (before == null || before.first != snapshot.ownerKey || !before.second)
        if (availableTabs?.contains(snapshot.tabId) == false) return
        if (!PageMediaPolicy.canPublish(foreground, backgroundEnabled, suppressed)) {
            tracker.setBackgroundAllowed(backgroundEnabled && snapshot.playing && snapshot.tabId == fullscreenAudioTabId)
            return
        }
        // Old timeupdate samples while its Pause command is in flight must not steal ownership back.
        if (owner !== tracker && (!snapshot.playing || (!started && !allowExistingPlayback))) return
        if (owner !== tracker) {
            val previous = owner
            release()
            // A user starting another page transfers system controls and audio ownership together.
            previous?.command("pause")
            owner = tracker
            revision++
        }
        tracker.setBackgroundAllowed(backgroundEnabled && snapshot.playing)
        if (!BrowserPlaybackService.attachPage(context, tracker)) {
            // Android can refuse a new foreground service after the app has left the foreground.
            // Do not promise background retention without a hosted session.
            if (!foreground) tracker.command("pause")
            release()
            return
        }
        if (snapshot.playing) cancelIdleTimeout()
        else if (idleTimeout == null) {
            val expire = Runnable {
                idleTimeout = null
                if (owner === tracker && tracker.snapshot?.playing != true) release()
            }
            idleTimeout = expire
            main.postDelayed(expire, 10 * 60_000L)
        }
    }

    fun configure(backgroundEnabled: Boolean, suppressed: Boolean, pauseForNative: Boolean,
        tabIds: Set<Long>, fullscreenTabId: Long?, fullscreenAudioTabId: Long?) {
        if (this.backgroundEnabled == backgroundEnabled && this.suppressed == suppressed &&
            availableTabs == tabIds && this.fullscreenAudioTabId == fullscreenAudioTabId) return
        this.backgroundEnabled = backgroundEnabled
        val newlySuppressed = suppressed && !this.suppressed
        this.suppressed = suppressed
        availableTabs = tabIds
        this.fullscreenAudioTabId = fullscreenAudioTabId
        // Preserve fullscreen only when it is the SAME tab/engine; another tab must be paused.
        if (newlySuppressed && PageMediaPolicy.pauseForHandoff(owner?.snapshot?.tabId,
                fullscreenTabId, pauseForNative)) owner?.command("pause")
        if (owner?.snapshot?.tabId?.let { it !in tabIds } == true) {
            owner?.command("pause")
            release()
        }
        reconcile()
        // Headphone mode uses the existing fullscreen controller/session, but the site still
        // needs its scoped visibility compatibility while that same WebView plays off screen.
        pages.keys.forEach { tracker ->
            tracker.setBackgroundAllowed(backgroundEnabled && tracker.snapshot?.playing == true &&
                (owner === tracker || tracker.snapshot?.tabId == fullscreenAudioTabId))
        }
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!value && !backgroundEnabled) owner?.command("pause")
        reconcile()
    }

    /** Paused media is retained too, so notification/headset Play can address the same document. */
    fun keepsRenderer(tabId: Long): Boolean {
        revision // Compose observes ownership, not high-frequency position updates.
        return PageMediaPolicy.keepRenderer(backgroundEnabled, suppressed, owner?.snapshot?.tabId, tabId)
    }

    private fun reconcile() {
        if (!PageMediaPolicy.canPublish(foreground, backgroundEnabled, suppressed)) {
            release()
            return
        }
        owner?.let { tracker ->
            changed(tracker)
        } ?: pages.keys.lastOrNull {
            it.snapshot?.let { state -> state.playing && availableTabs?.contains(state.tabId) != false } == true
        }?.let { changed(it, allowExistingPlayback = true) }
    }

    private fun release() {
        val previous = owner ?: return
        cancelIdleTimeout()
        owner = null
        revision++
        previous.setBackgroundAllowed(false)
        pages[previous]?.let { BrowserPlaybackService.detachPage(it, previous) }
    }

    private fun cancelIdleTimeout() {
        idleTimeout?.let { main.removeCallbacks(it) }
        idleTimeout = null
    }
}

internal object PageMediaPolicy {
    fun pauseForHandoff(ownerTabId: Long?, fullscreenTabId: Long?, richPlayer: Boolean): Boolean =
        ownerTabId != null && (richPlayer || ownerTabId != fullscreenTabId)

    fun canPublish(foreground: Boolean, backgroundEnabled: Boolean, suppressed: Boolean): Boolean =
        !suppressed && (foreground || backgroundEnabled)

    fun keepRenderer(backgroundEnabled: Boolean, suppressed: Boolean, ownerTabId: Long?, tabId: Long): Boolean =
        backgroundEnabled && !suppressed && ownerTabId == tabId
}
