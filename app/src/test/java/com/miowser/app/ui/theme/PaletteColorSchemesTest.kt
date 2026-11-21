package com.miowser.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PaletteColorSchemesTest {
    @Test fun nightSurfacesTextAndBordersStayInTheRequestedNeutralFamily() {
        val neutral = Color(0xFF091540)
        for (dark in listOf(false, true)) {
            val c = requireNotNull(AppColorTheme.NIGHT.paletteColorScheme(dark))
            val neutralRoles = c.surfaces().map { it.second } + listOf(
                c.onBackground, c.onSurface, c.onSurfaceVariant, c.outline, c.outlineVariant,
                c.inverseSurface, c.inverseOnSurface, c.surfaceTint,
                c.onPrimary, c.onPrimaryContainer, c.onSecondary, c.onSecondaryContainer,
                c.onTertiary, c.onTertiaryContainer,
                c.onPrimaryFixed, c.onPrimaryFixedVariant, c.onSecondaryFixed,
                c.onSecondaryFixedVariant, c.onTertiaryFixed, c.onTertiaryFixedVariant
            )
            neutralRoles.forEach { color ->
                assertTrue("Night neutral family replaced for dark=$dark: $color", onSourceLine(color, neutral))
            }
            // Navy remains visibly blue rather than becoming an almost-gray surface.
            assertTrue(c.surface.blue > c.surface.red)
            assertTrue(c.surface.blue > c.surface.green)
        }
    }

    private val customThemes = AppColorTheme.entries.filter {
        it != AppColorTheme.WALLPAPER && it != AppColorTheme.MIOWSER
    }

    @Test fun everyForegroundIsReadableInLightDarkAndAmoledSchemes() {
        assertEquals(14, customThemes.size)
        customThemes.forEach { theme ->
            val light = requireNotNull(theme.paletteColorScheme(false))
            val dark = requireNotNull(theme.paletteColorScheme(true))
            // AMOLED is a dark scheme with only these surfaces replaced by main.
            for ((mode, scheme) in listOf("light" to light, "dark" to dark, "amoled" to dark.amoledSurfaces())) {
                val context = "$theme/$mode"
                val pairs = listOf(
                    Triple("onPrimary", scheme.onPrimary, scheme.primary),
                    Triple("onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
                    Triple("onSecondary", scheme.onSecondary, scheme.secondary),
                    Triple("onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer),
                    Triple("onTertiary", scheme.onTertiary, scheme.tertiary),
                    Triple("onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer),
                    Triple("onBackground", scheme.onBackground, scheme.background),
                    Triple("inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface),
                    Triple("inversePrimary", scheme.inversePrimary, scheme.inverseSurface),
                    Triple("onError", scheme.onError, scheme.error),
                    Triple("onErrorContainer", scheme.onErrorContainer, scheme.errorContainer)
                )
                pairs.forEach { (role, foreground, background) ->
                    assertContrast("$context/$role", foreground, background, 4.5f)
                }
                scheme.surfaces().forEach { (surfaceName, background) ->
                    listOf("onSurface" to scheme.onSurface, "onSurfaceVariant" to scheme.onSurfaceVariant,
                        "primary" to scheme.primary, "secondary" to scheme.secondary,
                        "tertiary" to scheme.tertiary, "error" to scheme.error).forEach { (role, foreground) ->
                        assertContrast("$context/$role on $surfaceName", foreground, background, 4.5f)
                    }
                    // Interactive outlines/icons require 3:1. outlineVariant is a
                    // decorative divider role and intentionally has no such requirement.
                    assertContrast("$context/outline on $surfaceName", scheme.outline, background, 3f)
                }
                listOf(
                    listOf(scheme.primaryFixed, scheme.primaryFixedDim, scheme.onPrimaryFixed, scheme.onPrimaryFixedVariant),
                    listOf(scheme.secondaryFixed, scheme.secondaryFixedDim, scheme.onSecondaryFixed, scheme.onSecondaryFixedVariant),
                    listOf(scheme.tertiaryFixed, scheme.tertiaryFixedDim, scheme.onTertiaryFixed, scheme.onTertiaryFixedVariant)
                ).forEachIndexed { index, colors ->
                    for (background in colors.take(2)) for (foreground in colors.drop(2)) {
                        assertContrast("$context/fixed family $index", foreground, background, 4.5f)
                    }
                }
                allRoles(scheme).forEachIndexed { index, color ->
                    assertTrue("$context/role $index is unspecified or transparent", color != Color.Unspecified && color.alpha == 1f)
                }
            }
        }
    }

    @Test fun accentsAndContainersRetainSeveralIndependentSourceColors() {
        // Expected design choices expressed as RGB rather than production indices.
        val sources = mapOf(
            AppColorTheme.SEA to "1C4D8D 4988C4 BDE8F5",
            AppColorTheme.AMBER to "F48C06 D00000 FFBA08",
            AppColorTheme.BLACK_GOLD to "FCA311 14213D FFFFFF",
            AppColorTheme.SUMMER_OCEAN to "457B9D E63946 A8DADC",
            AppColorTheme.HALLOWEEN to "FF0000 830000 000000",
            AppColorTheme.RETRO to "728E6D E5CD82 15370D",
            AppColorTheme.SUNSET to "FF6F3C 155263 FFC93C",
            AppColorTheme.SPACE_MAN to "FF204E 00224D 5D0E41",
            AppColorTheme.WOOD to "B85C38 E0C097 5C3D2E",
            AppColorTheme.COTTON_CANDY to "BEAEE2 CDF0EA F7DBF0",
            AppColorTheme.GRASS to "5F8D4E A4BE7B E5D9B6",
            AppColorTheme.ICE_CREAM to "00B7CD DF301C FF9100",
            AppColorTheme.NIGHT to "ABD2FA 7692FF 1B2CC1",
            AppColorTheme.UMBRELLA to "2391FF F70063 FFF83F"
        )
        sources.forEach { (theme, hex) ->
            val seeds = hex.split(' ').map { Color(0xFF000000 or it.toLong(16)) }
            for (dark in listOf(false, true)) {
                val scheme = requireNotNull(theme.paletteColorScheme(dark))
                val accents = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
                val containers = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)
                assertEquals("$theme/$dark uses distinct accents", 3, accents.toSet().size)
                seeds.forEachIndexed { i, seed ->
                    assertTrue("$theme/$dark accent $i lost its RGB source", onSourceLine(accents[i], seed))
                    assertTrue("$theme/$dark container $i lost its RGB source", onSourceLine(containers[i], seed))
                    // A raw source already readable on every surface must be retained exactly.
                    if (scheme.surfaces().all { contrast(seed, it.second) >= 4.6f }) {
                        assertEquals("$theme/$dark unnecessarily adjusted source $i", seed, accents[i])
                    } else {
                        // A small step back toward the raw color should lose the requested
                        // safety margin: guards against unnecessarily washing out bright sources.
                        val towardSource = rgbMix(accents[i], seed, 0.04f)
                        assertTrue("$theme/$dark over-adjusted source $i",
                            scheme.surfaces().any { contrast(towardSource, it.second) < 4.6f })
                    }
                }
            }
        }
    }

    @Test fun allFourteenPalettesHaveVisiblyDifferentMulticolorSignatures() {
        for (dark in listOf(false, true)) {
            customThemes.forEachIndexed { index, a ->
                customThemes.drop(index + 1).forEach { b ->
                    val first = signature(requireNotNull(a.paletteColorScheme(dark)))
                    val second = signature(requireNotNull(b.paletteColorScheme(dark)))
                    val meanDistance = first.zip(second).map { (x, y) -> rgbDistance(x, y) }.average()
                    assertTrue("$a and $b are too similar in dark=$dark ($meanDistance)", meanDistance >= 0.08)
                }
            }
        }
    }

    @Test fun fixedRolesAreModeIndependentAndEverySchemeUsesCommonSemanticErrorRed() {
        for (dark in listOf(false, true)) {
            customThemes.forEach { theme ->
                val scheme = requireNotNull(theme.paletteColorScheme(dark))
                val expected = if (dark) listOf(0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6)
                    else listOf(0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002)
                assertEquals(expected.map { Color(it) }, listOf(scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer))
                assertEquals(Color.Black, scheme.scrim)
            }
        }
        customThemes.forEach { theme ->
            val light = requireNotNull(theme.paletteColorScheme(false))
            val dark = requireNotNull(theme.paletteColorScheme(true))
            assertEquals(allRoles(light).takeLast(12), allRoles(dark).takeLast(12))
            assertTrue(light.surface.luminance() > dark.surface.luminance())
            for (scheme in listOf(light, dark)) {
                val luminances = listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                    scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest).map { it.luminance() }
                assertTrue("$theme surface levels collapsed", luminances.zipWithNext().all { (a, b) -> if (scheme === dark) a < b else a > b })
            }
        }
    }

    private fun ColorScheme.amoledSurfaces() = copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080808),
        surfaceContainer = Color(0xFF101010), surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF222222), surfaceBright = Color(0xFF242424),
        surfaceVariant = Color(0xFF242424)
    )

    private fun ColorScheme.surfaces() = listOf(
        "background" to background, "surface" to surface, "surfaceDim" to surfaceDim,
        "surfaceBright" to surfaceBright, "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow, "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh, "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceVariant" to surfaceVariant
    )

    private fun allRoles(c: ColorScheme) = with(c) { listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
        surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer,
        onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceDim,
        surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow,
        surfaceContainerLowest, primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant,
        secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant,
        tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant
    ) }

    private fun signature(c: ColorScheme) = listOf(c.primary, c.secondary, c.tertiary,
        c.primaryContainer, c.secondaryContainer, c.tertiaryContainer)

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Float) {
        val actual = contrast(foreground, background)
        assertTrue("$label: $actual < $minimum ($foreground on $background)", actual >= minimum)
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    private fun rgbDistance(a: Color, b: Color): Double = sqrt(
        ((a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) +
            (a.blue - b.blue) * (a.blue - b.blue)).toDouble()
    )

    private fun rgbMix(a: Color, b: Color, amount: Float) = Color(
        a.red + (b.red - a.red) * amount, a.green + (b.green - a.green) * amount,
        a.blue + (b.blue - a.blue) * amount
    )

    // Projection onto either original-source-to-black or original-source-to-white
    // segment, allowing one 8-bit rounding step. Rejects unrelated hue substitution.
    private fun onSourceLine(actual: Color, source: Color): Boolean = listOf(0f, 1f).any { end ->
        val s = listOf(source.red, source.green, source.blue)
        val a = listOf(actual.red, actual.green, actual.blue)
        val denominator = s.sumOf { ((end - it) * (end - it)).toDouble() }
        val amount = if (denominator == 0.0) 0.0 else s.zip(a).sumOf { (seed, value) ->
            ((value - seed) * (end - seed)).toDouble()
        } / denominator
        amount in -0.005..1.005 && s.zip(a).all { (seed, value) ->
            abs(value - (seed + (end - seed) * amount)) <= 1.5 / 255
        }
    }
}
