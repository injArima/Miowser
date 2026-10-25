package com.miowser.app.browser

/** Positions inside Scaffold's browser-content area, above its bottom bar/insets. */
internal object BrowserFloatingActionLayout {
    const val EDGE_DP = 16
    const val JUMP_SIZE_DP = 48
    const val VIDEO_FACE_DP = 48
    const val VIDEO_TOUCH_DP = 60
    const val VIDEO_ICON_DP = 28
    const val GAP_DP = 12
    private const val VIDEO_TOUCH_OVERHANG_DP = (VIDEO_TOUCH_DP - VIDEO_FACE_DP) / 2
    const val VIDEO_RIGHT_INSET_DP = EDGE_DP - VIDEO_TOUCH_OVERHANG_DP
    const val VIDEO_BOTTOM_INSET_DP = EDGE_DP + JUMP_SIZE_DP + GAP_DP - VIDEO_TOUCH_OVERHANG_DP
}
