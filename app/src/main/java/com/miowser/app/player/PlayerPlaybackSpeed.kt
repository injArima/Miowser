package com.miowser.app.player

import com.miowser.app.ui.theme.PlayerSpeedControlTokens

internal fun validPlayerPlaybackSpeed(value: Float): Boolean = value.isFinite() &&
    value in PlayerSpeedControlTokens.minimum..PlayerSpeedControlTokens.maximum
