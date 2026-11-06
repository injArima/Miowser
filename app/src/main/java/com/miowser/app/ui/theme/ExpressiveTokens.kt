package com.miowser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Spacing scale for app-owned Compose layouts. Values are unchanged from the existing UI. */
object UiSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val compact = 10.dp
    val m = 12.dp
    val comfortable = 14.dp
    val l = 16.dp
    val roomy = 18.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/** Reused radii, including component radii between Material's default shape steps. */
object UiCorners {
    val r5 = RoundedCornerShape(5.dp)
    val r8 = RoundedCornerShape(8.dp)
    val r10 = RoundedCornerShape(10.dp)
    val r12 = RoundedCornerShape(12.dp)
    val r14 = RoundedCornerShape(14.dp)
    val r16 = RoundedCornerShape(16.dp)
    val r18 = RoundedCornerShape(18.dp)
    val r20 = RoundedCornerShape(20.dp)
    val r24 = RoundedCornerShape(24.dp)
    val r32 = RoundedCornerShape(32.dp)
}

object UiIconSize {
    val small = 18.dp
    val menu = 20.dp
    val standard = 24.dp
    val featured = 28.dp
}

object UiControlSize {
    val compact = 40.dp
    val action = 44.dp
    val touchTarget = 48.dp
    val prominent = 64.dp
}

object UiElevation {
    val low = 2.dp
    val raised = 4.dp
    val medium = 6.dp
    val high = 8.dp
    val floating = 12.dp
}

/** Fixed player cues stay distinct from dynamic wallpaper colors for readability. */
object PlayerUiColors {
    val backdrop = Color.Black
    val content = Color.White
    val panel = Color(0xFF151313)
    val sidePanel = Color(0xFF17191D)
    val disabledControl = Color(0xFF9C9C9C)
    val seekBackward = Color(0xFFFFAAA5)
    val seekForward = Color(0xFFA8E6B0)
    val caution = Color(0xFFFFD875)
    val guidanceSurface = Color(0xFF2D291E)
    val subtitlePreview = Color(0xFF8C8C8C)
}

object UiSemanticColors {
    val secureOnLight = Color(0xFF137333)
    val secureOnDark = Color(0xFF81C995)
}

/** Semantic aliases used by shared app surfaces. */
object ExpressiveTokens {
    val pageGutter = UiSpacing.l
    val sectionGap = UiSpacing.xl
    val itemGap = UiSpacing.compact
    val compactIcon = UiIconSize.standard
    val featuredIcon = UiIconSize.featured
    val minimumTouchTarget = UiControlSize.touchTarget
}

val ExpressiveShapes = Shapes(
    extraSmall = UiCorners.r8,
    small = UiCorners.r12,
    medium = UiCorners.r18,
    large = UiCorners.r24,
    extraLarge = UiCorners.r32
)
