package com.miowser.app.browser

import kotlin.math.abs
import kotlin.math.max

/** Vertical and diagonal movement must never count as an intent to close a tab. */
internal fun startsTabCloseSwipe(dx: Float, dy: Float, touchSlop: Float): Boolean =
    abs(dx) > touchSlop && abs(dx) >= abs(dy) * 2f

internal fun commitsTabCloseSwipe(dx: Float, dy: Float, width: Float, minimumDistance: Float): Boolean =
    abs(dx) >= max(width * 0.68f, minimumDistance) && abs(dx) >= abs(dy) * 1.4f
