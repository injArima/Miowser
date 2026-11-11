package com.miowser.app.browser.video

import com.miowser.app.player.validPlayerPlaybackSpeed
import com.miowser.app.ui.theme.PlayerSpeedControlTokens
import kotlin.math.abs

/** One bounded retry budget per owned source and explicit speed choice; never fight a site forever. */
internal class BrowserPlaybackSpeedRestore(private val selected: Double?) {
    private var attempts = 0

    fun next(current: Double, holding: Boolean): Double? {
        val target = selected?.takeIf { it.isFinite() && validPlayerPlaybackSpeed(it.toFloat()) } ?: return null
        if (holding || abs(current - target) < PlayerSpeedControlTokens.acknowledgementTolerance || attempts >= MAX_ATTEMPTS) return null
        attempts++
        return target
    }

    companion object {
        const val RETRY_DELAY_MS = 350L
        private const val MAX_ATTEMPTS = 3
    }
}
