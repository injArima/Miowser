package com.miowser.app.player

import androidx.media3.common.PlaybackException
import kotlin.math.abs

internal data class PlayerErrorPresentation(
    val title: String,
    val message: String,
    val canRetry: Boolean = true
)

/** Turns Media3's implementation-focused failure codes into useful player recovery guidance. */
internal fun playerErrorPresentation(errorCode: Int): PlayerErrorPresentation = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerErrorPresentation(
        title = "Connection problem",
        message = "Check your connection, then try loading the video again."
    )

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlayerErrorPresentation(
        title = "Source unavailable",
        message = "The video server rejected this request. The link may have expired."
    )

    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlayerErrorPresentation(
        title = "Video not found",
        message = "The source or downloaded file is no longer available.",
        canRetry = false
    )

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlayerErrorPresentation(
        title = "Video access blocked",
        message = "Miowser does not have permission to open this source.",
        canRetry = false
    )

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlayerErrorPresentation(
        title = "Unsupported or damaged stream",
        message = "The server returned media that could not be read correctly."
    )

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED -> PlayerErrorPresentation(
        title = "Video could not be decoded",
        message = "This device could not decode the selected video track. Try again or choose another source."
    )

    in 6_000..6_999 -> PlayerErrorPresentation(
        title = "Protected video unsupported",
        message = "This protected stream cannot be opened in Miowser's rich player.",
        canRetry = false
    )

    else -> PlayerErrorPresentation(
        title = "Playback failed",
        message = "Try loading the video again. If it still fails, return and choose another source."
    )
}

internal enum class PlayerGestureMode { UNDECIDED, SEEK, BRIGHTNESS, VOLUME }

internal enum class PlayerDoubleTapAction { NONE, SEEK_BACKWARD, TOGGLE_PLAY_PAUSE, SEEK_FORWARD }

internal fun playerGesturePointAllowed(
    y: Float,
    height: Float,
    protectedTop: Float,
    protectedBottom: Float
): Boolean = y >= protectedTop && y <= height - protectedBottom

/** Left/right seek zones stay generous while portrait gives the middle action slightly more room. */
internal fun classifyPlayerDoubleTap(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    protectedTop: Float,
    protectedBottom: Float,
    isLandscape: Boolean
): PlayerDoubleTapAction {
    if (width <= 0f || height <= 0f || !playerGesturePointAllowed(y, height, protectedTop, protectedBottom)) {
        return PlayerDoubleTapAction.NONE
    }
    val centerFraction = if (isLandscape) 0.28f else 0.34f
    val sideFraction = (1f - centerFraction) / 2f
    return when {
        x < width * sideFraction -> PlayerDoubleTapAction.SEEK_BACKWARD
        x > width * (1f - sideFraction) -> PlayerDoubleTapAction.SEEK_FORWARD
        else -> PlayerDoubleTapAction.TOGGLE_PLAY_PAUSE
    }
}

/**
 * With controls visible, horizontal seeking starts only in the unobstructed video band. Hidden
 * controls restore the entire usable player surface. Portrait always preserves its lower control
 * area, represented by [protectedBottom].
 */
internal fun playerSeekGestureAllowed(
    y: Float,
    height: Float,
    protectedTop: Float,
    protectedBottom: Float,
    controlsVisible: Boolean,
    isLandscape: Boolean
): Boolean {
    if (!playerGesturePointAllowed(y, height, protectedTop, protectedBottom)) return false
    if (!controlsVisible) return true
    val seekTop = maxOf(protectedTop, height * if (isLandscape) 0.20f else 0.12f)
    val seekBottom = minOf(height - protectedBottom, height * if (isLandscape) 0.60f else 0.80f)
    return y in seekTop..seekBottom
}

/**
 * Waits for a clearly dominant axis before claiming a drag. Vertical gestures starting inside the
 * system-edge exclusion area are deliberately ignored so back/navigation gestures retain priority.
 */
internal fun classifyPlayerGesture(
    dragX: Float,
    dragY: Float,
    startedAtX: Float,
    width: Float,
    edgeInset: Float,
    allowSeek: Boolean = true,
    verticalMode: PlayerGestureMode? = if (startedAtX < width / 2f) {
        PlayerGestureMode.BRIGHTNESS
    } else {
        PlayerGestureMode.VOLUME
    },
    dominanceRatio: Float = 1.15f
): PlayerGestureMode {
    val horizontal = abs(dragX)
    val vertical = abs(dragY)
    if (horizontal == 0f && vertical == 0f) return PlayerGestureMode.UNDECIDED
    if (horizontal >= vertical * dominanceRatio) {
        return if (allowSeek) PlayerGestureMode.SEEK else PlayerGestureMode.UNDECIDED
    }
    if (vertical < horizontal * dominanceRatio) return PlayerGestureMode.UNDECIDED
    if (startedAtX <= edgeInset || startedAtX >= width - edgeInset) {
        return PlayerGestureMode.UNDECIDED
    }
    return verticalMode ?: PlayerGestureMode.UNDECIDED
}
