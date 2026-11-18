package com.miowser.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeSelectionCodecTest {
    @Test fun everyThemeAndAppearanceHasAStableIndependentIdentifier() {
        for (palette in AppColorTheme.entries) {
            assertEquals(palette, ThemeSelectionCodec.palette(palette.name))
            for (mode in AppThemeMode.entries) assertEquals(mode, ThemeSelectionCodec.mode(mode.name))
        }
    }

    @Test fun oldSettingsKeepTheirModeAndDefaultToWallpaperColors() {
        assertEquals(AppThemeMode.AMOLED, ThemeSelectionCodec.mode("AMOLED"))
        assertEquals(AppColorTheme.WALLPAPER, ThemeSelectionCodec.palette(null))
    }

    @Test fun malformedAndUnknownRestoredPreferencesCannotBreakStartup() {
        for (value in listOf(null, "", "unknown", 42, true, setOf("SEA"))) {
            assertEquals(AppThemeMode.SYSTEM, ThemeSelectionCodec.mode(value))
            assertEquals(AppColorTheme.WALLPAPER, ThemeSelectionCodec.palette(value))
        }
    }
}
