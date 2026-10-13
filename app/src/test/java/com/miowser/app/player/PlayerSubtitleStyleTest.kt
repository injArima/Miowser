package com.miowser.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSubtitleStyleTest {
    @Test fun oldCaptionChoicesMapToTheClosestNewPreset() {
        assertEquals(CaptionVisualPreset.WHITE_OUTLINE, legacyPreset(false, false, false))
        assertEquals(CaptionVisualPreset.WHITE_SHADOW, legacyPreset(false, false, true))
        assertEquals(CaptionVisualPreset.WHITE_TRANSLUCENT, legacyPreset(false, true, false))
        assertEquals(CaptionVisualPreset.YELLOW_SHADOW, legacyPreset(true, false, true))
        assertEquals(CaptionVisualPreset.YELLOW_TRANSLUCENT, legacyPreset(true, true, false))
    }

    @Test fun subtitleDelayMovesVttLookupWithoutGoingBeforeStart() {
        assertEquals(4_750L, PlayerSubtitleStyle(delayMs = 250L).vttPositionMs(5_000L))
        assertEquals(5_250L, PlayerSubtitleStyle(delayMs = -250L).vttPositionMs(5_000L))
        assertEquals(0L, PlayerSubtitleStyle(delayMs = 500L).vttPositionMs(100L))
    }

    @Test fun customOutlineOverridesPresetAndResetRestoresIt() {
        assertEquals(true, PlayerSubtitleStyle().resolvedOutlineEnabled())
        assertEquals(false, PlayerSubtitleStyle(outlineEnabled = false).resolvedOutlineEnabled())
        assertEquals(true, PlayerSubtitleStyle(
            preset = CaptionVisualPreset.WHITE_SHADOW, outlineEnabled = true
        ).resolvedOutlineEnabled())
    }
}
