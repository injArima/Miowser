package com.miowser.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppColorThemeTest {
    @Test fun galleryShowsNumberedFamiliesFirstWithoutDroppingAnySourceSwatches() {
        assertEquals(listOf(0xFFABD2FA, 0xFF7692FF, 0xFF1B2CC1, 0xFF091540).map { Color(it).toArgb() },
            AppColorTheme.NIGHT.familyOrderedSwatches().map { it.toArgb() })
        AppColorTheme.entries.forEach { theme ->
            val preview = theme.familyOrderedSwatches()
            assertEquals(theme.name, theme.swatches.toSet(), preview.toSet())
            assertEquals(theme.name, theme.swatches.size, preview.size)
        }
    }

    @Test fun catalogPreservesPreferenceIdsLabelsAndReferenceOrder() {
        assertEquals(
            listOf("WALLPAPER", "MIOWSER", "SEA", "AMBER", "BLACK_GOLD", "SUMMER_OCEAN",
                "HALLOWEEN", "RETRO", "SUNSET", "SPACE_MAN", "WOOD", "COTTON_CANDY",
                "GRASS", "ICE_CREAM", "NIGHT", "UMBRELLA"),
            AppColorTheme.entries.map { it.name }
        )
        assertEquals(
            listOf("Wallpaper", "Miowser", "Sea", "Amber", "Black Gold", "Summer Ocean",
                "Halloween", "Retro", "Sunset", "Space Man", "Wood", "Cotton Candy",
                "Grass", "Ice Cream", "Night", "Umbrella"),
            AppColorTheme.entries.map { it.label }
        )
    }

    @Test fun swatchesAreExactOpaqueSourceRgbWithoutTonalAdjustment() {
        val expected = mapOf(
            AppColorTheme.SEA to "0F2854 1C4D8D 4988C4 BDE8F5",
            AppColorTheme.AMBER to "03071E 370617 6A040F 9D0208 D00000 DC2F02 E85D04 F48C06 FAA307 FFBA08",
            AppColorTheme.BLACK_GOLD to "000000 14213D FCA311 E5E5E5 FFFFFF",
            AppColorTheme.SUMMER_OCEAN to "E63946 F1FAEE A8DADC 457B9D 1D3557",
            AppColorTheme.HALLOWEEN to "000000 830000 BC0202 FF0000",
            AppColorTheme.RETRO to "15370D 728E6D E5CD82 FFF2B4",
            AppColorTheme.SUNSET to "155263 FF6F3C FF9A3C FFC93C",
            AppColorTheme.SPACE_MAN to "FF204E A0153E 5D0E41 00224D",
            AppColorTheme.WOOD to "2D2424 5C3D2E B85C38 E0C097",
            AppColorTheme.COTTON_CANDY to "CDF0EA F9F9F9 F7DBF0 BEAEE2",
            AppColorTheme.GRASS to "E5D9B6 A4BE7B 5F8D4E 285430",
            AppColorTheme.ICE_CREAM to "DF301C FF9100 FFF1D1 00B7CD",
            AppColorTheme.NIGHT to "091540 1B2CC1 7692FF ABD2FA",
            AppColorTheme.UMBRELLA to "2391FF FFF83F FFCD54 F70063"
        )
        assertEquals(14, expected.size)
        expected.forEach { (theme, hex) ->
            assertEquals(theme.name, hex.split(' ').map { Color(0xFF000000 or it.toLong(16)).toArgb() },
                theme.swatches.map { it.toArgb() })
        }
        assertEquals(emptyList<Color>(), AppColorTheme.WALLPAPER.swatches)
        assertEquals(listOf(MiowserBlue, MiowserTeal, MiowserWarm), AppColorTheme.MIOWSER.swatches)
    }

    @Test fun existingDefaultAndWallpaperSchemesRemainOwnedByThemeEntryPoint() {
        for (theme in listOf(AppColorTheme.WALLPAPER, AppColorTheme.MIOWSER)) {
            for (dark in listOf(false, true)) assertNull(theme.paletteColorScheme(dark))
        }
    }
}
