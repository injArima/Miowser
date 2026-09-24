package com.miowser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared dimensions for app-owned surfaces; no experimental Material dependency. */
object ExpressiveTokens {
    val pageGutter = 16.dp
    val sectionGap = 20.dp
    val itemGap = 10.dp
    val compactIcon = 24.dp
    val featuredIcon = 28.dp
    val minimumTouchTarget = 48.dp
    const val compactMotionMillis = 180
}

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
