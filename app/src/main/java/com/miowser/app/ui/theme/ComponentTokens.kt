package com.miowser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared component contracts. Keep layout values here when they belong to a component. */
object AddressBarTokens {
    val fieldHeight = UiControlSize.touchTarget
    val accentWidth = UiControlSize.touchTarget
    val accentHeight = UiControlSize.action
    val submitSize = UiControlSize.compact
}

object BrowserHomeTokens {
    val heroTopSpace = 76.dp
    val heroBottomSpace = 22.dp
    val speedDialTile = 46.dp
    val gridMinCell = 52.dp
    val gridHorizontalPadding = UiSpacing.m
    val gridVerticalPadding = UiSpacing.l
    val gridColumnGap = 6.dp
}

object BrowserFindTokens {
    val horizontalPadding = 6.dp
    val verticalPadding = UiSpacing.xs
    val fieldHeight = UiControlSize.touchTarget
    val clearButtonMaxWidth = UiControlSize.compact
    val counterMinWidth = UiSpacing.xxxl
}

object BottomNavigationTokens {
    val roomyWidthThreshold = 336.dp
    val roomySidePadding = UiSpacing.m
    val compactSidePadding = UiSpacing.xs
    val verticalPadding = UiSpacing.s
    val buttonSize = UiControlSize.touchTarget
}

object BrowserMenuTokens {
    val tileSize = UiControlSize.touchTarget
    val tileGap = UiSpacing.compact
    val tileContentSize = UiControlSize.compact
    val tileIconSize = UiIconSize.standard
    val tileShape = UiCorners.r16
    val tileContentShape = UiCorners.r14
    val tileContentPressedShape = UiCorners.r8
    val sectionShape = UiCorners.r24
    val sectionInset = UiSpacing.s
    val actionIconContainerSize = UiControlSize.action
    val actionIconSize = 22.dp
}

object SheetComponentTokens {
    val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val handleWidth = UiSpacing.xxxl
    val handleHeight = UiSpacing.xs
    val handleTopPadding = 7.dp
    val handleBottomPadding = UiSpacing.roomy
}

object ConfirmationComponentTokens {
    val horizontalPadding = 22.dp
    val verticalPadding = 26.dp
    val actionMinHeight = 52.dp
    val actionGap = UiSpacing.compact
    const val normalBorderAlpha = 0.38f
    const val cautionFillAlpha = 0.12f
}

object SnackbarComponentTokens {
    val horizontalPadding = UiSpacing.m
    val actionHorizontalPadding = UiSpacing.s
    val contentGap = UiSpacing.m
    val markSize = UiIconSize.featured
}

object HistoryRowTokens {
    val minHeight = 80.dp
    val siteIconSize = UiControlSize.action
    val siteIconShape = UiCorners.r14
    val segmentGap = UiSpacing.xxs
    val outerCorner = UiSpacing.xxl
    val innerCorner = 6.dp
    val headerHeight = UiControlSize.touchTarget
}

object TabHistoryMenuTokens {
    val minWidth = 260.dp
    val maxWidth = 320.dp
    val maxHeight = 376.dp
}

object HubLibraryTokens {
    val gridMinCell = 168.dp
    val gridPadding = UiSpacing.m
    val gridBottomClearance = 80.dp
    val railHeight = 56.dp
    val tabSwipeDistance = 40.dp
    val busyOutlineWidth = 2.dp
    const val busyOutlineCycleMillis = 1_400
    const val busyOutlineFraction = 0.24f
}

object HubVideoCardTokens {
    val listArtworkWidth = 136.dp
    val listArtworkHeight = 84.dp
    val centerArtworkSize = 76.dp
    val badgeHorizontalPadding = 6.dp
    val badgeVerticalPadding = 3.dp
    val artworkTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    const val artworkOverlayAlpha = 0.18f
}

/** Material 3 text-button family; visual emphasis follows the action's role. */
object ActionButtonTokens {
    val shape = UiCorners.r24
    val pressedShape = UiCorners.r12
    val minHeight = UiControlSize.touchTarget
    val cautionOutlineWidth = 1.dp
    const val cautionOutlineAlpha = 0.5f
    const val cautionContainerAlpha = 0.15f
}

/** Loading sizes keep Expressive indicators proportional to their surrounding controls. */
object LoadingIndicatorTokens {
    val address = UiIconSize.featured
    val compact = UiIconSize.standard
    val pullRefresh = UiControlSize.compact
}

object PlayerSpeedControlTokens {
    const val minimum = 0.25f
    const val normal = 1f
    const val maximum = 3f
    const val increment = 0.01f
    const val sliderSteps = 274
    const val acknowledgementTolerance = increment / 2f
    const val feedbackTimeoutMs = 5_000L
    const val slowRepeatMs = 160L
    const val mediumRepeatMs = 100L
    const val fastRepeatMs = 60L
    const val mediumRepeatAfter = 8
    const val fastRepeatAfter = 24
    val menuWidth = 320.dp
    val fieldWidth = 184.dp
}

object SubtitlePanelTokens {
    const val cueAnchorFraction = 0.28f
    const val selectedCueAlpha = 0.24f
    const val previewCueAlpha = 0.08f
    const val controlContainerAlpha = 0.18f
    const val secondaryContentAlpha = 0.7f
    val cueTimeWidth = 48.dp
    val cueGap = UiSpacing.xxs
    val delayEditorFieldWidth = 112.dp
    val delayHelpMaxWidth = 300.dp
    val delayHelpOffset = UiControlSize.touchTarget
    const val delayHelpDurationMs = 8_000L
    const val tracksWeight = 0.28f
    const val inspectionWeight = 0.72f
    const val previewTextFraction = 0.12f
    const val settingsPreviewHeightFraction = 0.32f
    val settingsPreviewMinHeight = 96.dp
    val settingsPreviewMaxHeight = 180.dp
    val inspectionPreviewHeight = 72.dp
    val valueWidth = 56.dp
    val delayFieldWidth = 64.dp
    val colorSize = 30.dp
    val colorBorder = 1.dp
    val selectedColorBorder = 3.dp
}
