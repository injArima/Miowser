package com.miowser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Non-player component contracts. Keep layout values here when they belong to a component. */
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
    val minHeight = UiControlSize.touchTarget
}
