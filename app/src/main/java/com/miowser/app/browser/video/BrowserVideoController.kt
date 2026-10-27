package com.miowser.app.browser.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BrowserVideoAction(val wireName: String, val family: String) {
    PLAY("play", "transport"), PAUSE("pause", "transport"), SEEK("seek", "seek"),
    SKIP("skip", "seek"), RATE("rate", "rate"), RICH_UI("richUi", "ui"),
    MUTE("mute", "mute"), SCALE("scale", "scale"), CAPTION("caption", "caption"), AUDIO("audio", "audio"),
    AUDIO_ONLY("audioOnly", "audioOnly"),
    HOLD_RATE("holdRate", "rate"), RELEASE_RATE("releaseRate", "rate")
}

internal data class BrowserVideoCommand(val owner: LiveVideoId, val attempt: Long,
    val action: BrowserVideoAction, val value: Double? = null, val sourceRevision: Long = 0)

internal enum class BrowserVideoPhase { IDLE, ACTIVE, SUSPENDED, RECONNECTING, UNAVAILABLE }

internal data class BrowserSeekWindow(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

internal fun LiveVideoState.browserSeekWindow(): BrowserSeekWindow? = when {
    seekable.isNotEmpty() -> BrowserSeekWindow(seekable.minOf { it.startMs }, seekable.maxOf { it.endMs })
    durationMs != null && durationMs > 0 && readyState >= 1 -> BrowserSeekWindow(0, durationMs)
    else -> null
}

internal data class BrowserVideoPlaybackState(
    val session: LiveVideoSession? = null,
    val attempt: Long = 0,
    val pendingSeekMs: Long? = null,
    val message: String? = null,
    val phase: BrowserVideoPhase = BrowserVideoPhase.IDLE,
    val audioOnly: Boolean = false
) {
    val controlsEnabled: Boolean get() = session != null && phase == BrowserVideoPhase.ACTIVE
    val canSeek: Boolean get() = controlsEnabled && session?.state?.browserSeekWindow()?.durationMs?.let { it > 0 } == true
}

/** UI-facing contract for this add-on; the existing Media3 player keeps its own backend. */
internal interface BrowserPlaybackControls {
    val state: StateFlow<BrowserVideoPlaybackState>
    fun playPause()
    fun pause()
    fun skip(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Double)
    fun beginSpeedHold()
    fun endSpeedHold()
    fun setMuted(muted: Boolean)
    fun setAudioOnly(enabled: Boolean)
    fun setScale(scale: BrowserVideoScale)
    fun selectCaption(id: Long?)
    fun selectAudio(id: Long)
    fun setRichUi(attached: Boolean)
    fun clearMessage()
}

/** Main-owner state. Commands never contain a URL and can only address the bound fullscreen video. */
internal class BrowserVideoController : BrowserPlaybackControls {
    private val mutable = MutableStateFlow(BrowserVideoPlaybackState())
    override val state = mutable.asStateFlow()
    private var sender: ((BrowserVideoCommand) -> Boolean)? = null
    private var desiredPlaying: Boolean? = null
    private var audioOnlyBeforeCommand: Boolean? = null

    fun connect(sender: (BrowserVideoCommand) -> Boolean) { this.sender = sender }
    fun attach(session: LiveVideoSession, attempt: Long) {
        if (mutable.value.session?.id == session.id && mutable.value.attempt == attempt) {
            sample(session)
        } else {
            desiredPlaying = null
            mutable.value = BrowserVideoPlaybackState(session, attempt, phase = BrowserVideoPhase.ACTIVE)
        }
    }
    fun sample(session: LiveVideoSession) {
        if (mutable.value.session?.id != session.id) return
        val changed = mutable.value.session?.state?.sourceRevision != session.state.sourceRevision
        if (changed || desiredPlaying == session.state.playing) desiredPlaying = null
        mutable.value = mutable.value.copy(session = session,
            pendingSeekMs = if (changed) null else mutable.value.pendingSeekMs,
            message = if (changed) null else mutable.value.message)
    }
    fun detach(reason: String? = null) {
        desiredPlaying = null
        mutable.value = BrowserVideoPlaybackState(message = reason,
            phase = if (reason == null) BrowserVideoPhase.IDLE else BrowserVideoPhase.UNAVAILABLE)
    }
    fun suspend() { transition(BrowserVideoPhase.SUSPENDED) }
    fun reconnecting() { transition(BrowserVideoPhase.RECONNECTING) }
    fun recovered() { transition(BrowserVideoPhase.ACTIVE) }
    private fun transition(phase: BrowserVideoPhase) {
        if (mutable.value.session == null) return
        desiredPlaying = null
        mutable.value = mutable.value.copy(phase = phase, pendingSeekMs = null, message = null)
    }
    fun disconnect() { detach(); sender = null }

    override fun playPause() {
        if (!mutable.value.controlsEnabled) return
        val sample = mutable.value.session?.state ?: return
        val target = !(desiredPlaying ?: sample.playing)
        desiredPlaying = target
        if (!send(if (target) BrowserVideoAction.PLAY else BrowserVideoAction.PAUSE)) desiredPlaying = null
    }
    override fun pause() { desiredPlaying = false; send(BrowserVideoAction.PAUSE) }
    override fun skip(deltaMs: Long) {
        if (!mutable.value.canSeek) return
        val position = mutable.value.session?.state?.positionMs ?: return
        val window = mutable.value.session?.state?.browserSeekWindow() ?: return
        val delta = deltaMs.coerceIn(-60_000, 60_000)
        mutable.value = mutable.value.copy(pendingSeekMs = (position + delta).coerceIn(window.startMs, window.endMs))
        // The page applies this delta to its fresh currentTime, not this sampled clock value.
        send(BrowserVideoAction.SKIP, delta / 1000.0)
    }
    override fun seekTo(positionMs: Long) {
        if (!mutable.value.controlsEnabled) return
        val sample = mutable.value.session?.state ?: return
        val window = sample.browserSeekWindow() ?: return
        val target = sample.seekTarget(positionMs) ?: positionMs.coerceIn(window.startMs, window.endMs)
        mutable.value = mutable.value.copy(pendingSeekMs = target)
        send(BrowserVideoAction.SEEK, target / 1000.0)
    }
    override fun setSpeed(speed: Double) {
        if (speed.isFinite() && speed in 0.25..3.0) send(BrowserVideoAction.RATE, speed)
    }
    override fun beginSpeedHold() { send(BrowserVideoAction.HOLD_RATE) }
    override fun endSpeedHold() { send(BrowserVideoAction.RELEASE_RATE) }
    override fun setMuted(muted: Boolean) { send(BrowserVideoAction.MUTE, if (muted) 1.0 else 0.0) }
    override fun setAudioOnly(enabled: Boolean) {
        if (mutable.value.audioOnly == enabled) return
        val previous = mutable.value.audioOnly
        mutable.value = mutable.value.copy(audioOnly = enabled)
        audioOnlyBeforeCommand = previous
        if (!send(BrowserVideoAction.AUDIO_ONLY, if (enabled) 1.0 else 0.0)) {
            mutable.value = mutable.value.copy(audioOnly = previous)
            audioOnlyBeforeCommand = null
        }
    }
    override fun setScale(scale: BrowserVideoScale) { send(BrowserVideoAction.SCALE, scale.ordinal.toDouble()) }
    override fun selectCaption(id: Long?) {
        val tracks = mutable.value.session?.state?.captions ?: return
        if (id == null || tracks.any { it.id == id }) send(BrowserVideoAction.CAPTION, id?.toDouble() ?: -1.0)
    }
    override fun selectAudio(id: Long) {
        if (mutable.value.session?.state?.audioTracks?.any { it.id == id } == true) send(BrowserVideoAction.AUDIO, id.toDouble())
    }
    override fun setRichUi(attached: Boolean) {
        send(BrowserVideoAction.RICH_UI, if (attached) 1.0 else 0.0)
    }
    override fun clearMessage() { mutable.value = mutable.value.copy(message = null) }

    fun started(action: BrowserVideoAction) {
        if (action.family == "seek" && mutable.value.pendingSeekMs != null) {
            mutable.value = mutable.value.copy(pendingSeekMs = mutable.value.session?.state?.positionMs)
        }
    }

    fun completed(action: BrowserVideoAction, ok: Boolean) {
        if (mutable.value.session == null) return
        if (action.family == "transport") desiredPlaying = null
        if (action == BrowserVideoAction.AUDIO_ONLY) {
            if (!ok) audioOnlyBeforeCommand?.let { previous ->
                mutable.value = mutable.value.copy(audioOnly = previous)
            }
            audioOnlyBeforeCommand = null
        }
        mutable.value = mutable.value.copy(
            pendingSeekMs = if (action.family == "seek") null else mutable.value.pendingSeekMs,
            message = if (ok) null else when (action.family) {
                "seek" -> "The website didn't confirm that seek. Try another position."
                "rate" -> "The website couldn't apply that playback speed."
                "caption" -> "The website couldn't select that subtitle track."
                "audio" -> "The website couldn't select that audio track."
                "audioOnly" -> "The website couldn't switch audio-only playback."
                "scale" -> "The website couldn't change the video size."
                else -> "The website couldn't complete that player action."
            })
    }

    private fun send(action: BrowserVideoAction, value: Double? = null): Boolean {
        val current = mutable.value
        val owner = current.session?.id ?: return false
        if (!current.controlsEnabled && action !in setOf(BrowserVideoAction.RICH_UI,
                BrowserVideoAction.RELEASE_RATE, BrowserVideoAction.PAUSE)) return false
        val sent = sender?.invoke(BrowserVideoCommand(owner, current.attempt, action, value,
            // Dismissing PiP pauses the current owned element, including a just-changed source.
            if (action == BrowserVideoAction.PAUSE && !current.controlsEnabled) -1 else current.session.state.sourceRevision)) == true
        if (!sent) completed(action, false)
        return sent
    }
}
