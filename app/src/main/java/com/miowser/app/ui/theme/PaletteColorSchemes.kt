package com.miowser.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.pow

/** Wallpaper and Miowser are resolved by the existing theme entry point. */
internal fun AppColorTheme.paletteColorScheme(darkTheme: Boolean): ColorScheme? =
    paletteDefinitions[this]?.let { if (darkTheme) it.dark else it.light }

/** Show families 1–4 first, followed by any other retained source colors. */
internal fun AppColorTheme.familyOrderedSwatches(): List<Color> =
    paletteDefinitions[this]?.let {
        (listOf(it.primary, it.secondary, it.tertiary, it.neutral) + swatches).distinct()
    } ?: swatches

// Indices refer to the exact swatches in AppColorTheme. Each accent family has its own
// source; surface tinting is independent so a multicolor palette never becomes one hue.
private val paletteDefinitions = mapOf(
    AppColorTheme.SEA to AppColorTheme.SEA.palette(1, 2, 3, 0),
    AppColorTheme.AMBER to AppColorTheme.AMBER.palette(2, 1, 3, 0),
    AppColorTheme.BLACK_GOLD to AppColorTheme.BLACK_GOLD.palette(2, 1, 3, 0),
    AppColorTheme.SUMMER_OCEAN to AppColorTheme.SUMMER_OCEAN.palette(2, 0, 1, 3),
    AppColorTheme.HALLOWEEN to AppColorTheme.HALLOWEEN.palette(3, 1, 0, 0),
    AppColorTheme.RETRO to AppColorTheme.RETRO.palette(1, 2, 0, 0),
    AppColorTheme.SUNSET to AppColorTheme.SUNSET.palette(1, 0, 3, 0),
    AppColorTheme.SPACE_MAN to AppColorTheme.SPACE_MAN.palette(0, 3, 2, 3),
    AppColorTheme.WOOD to AppColorTheme.WOOD.palette(2, 3, 1, 0),
    AppColorTheme.COTTON_CANDY to AppColorTheme.COTTON_CANDY.palette(3, 0, 2, 3),
    AppColorTheme.GRASS to AppColorTheme.GRASS.palette(2, 1, 0, 3),
    AppColorTheme.ICE_CREAM to AppColorTheme.ICE_CREAM.palette(3, 0, 1, 2),
    AppColorTheme.NIGHT to AppColorTheme.NIGHT.palette(3, 2, 1, 0),
    AppColorTheme.UMBRELLA to AppColorTheme.UMBRELLA.palette(0, 3, 1, 2)
)

private fun AppColorTheme.palette(primary: Int, secondary: Int, tertiary: Int, neutral: Int) =
    PaletteDefinition(swatches[primary], swatches[secondary], swatches[tertiary], swatches[neutral])

private class PaletteDefinition(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color
) {
    val light: ColorScheme by lazy { scheme(dark = false) }
    val dark: ColorScheme by lazy { scheme(dark = true) }

    fun scheme(dark: Boolean): ColorScheme {
        // Family 4 owns surfaces, text, and boundaries. Preserve its hue instead
        // of replacing most of the requested color with gray.
        val neutralSeed = neutral
        val variantSeed = neutral
        val surface = neutralSeed.tone(if (dark) 6f else 98f)
        val surfaceDim = neutralSeed.tone(if (dark) 6f else 87f)
        val surfaceBright = neutralSeed.tone(if (dark) 24f else 98f)
        val lowest = neutralSeed.tone(if (dark) 4f else 100f)
        val low = neutralSeed.tone(if (dark) 10f else 96f)
        val container = neutralSeed.tone(if (dark) 12f else 94f)
        val high = neutralSeed.tone(if (dark) 17f else 92f)
        val highest = neutralSeed.tone(if (dark) 22f else 90f)
        val variant = variantSeed.tone(if (dark) 24f else 90f)
        // Fit text accents to the most demanding surface, which also protects all
        // intermediate containers and the black/08/10/18/22/24 AMOLED surfaces.
        val limitingSurface = if (dark) surfaceBright else surfaceDim
        val p = primary.accent(limitingSurface, dark)
        val s = secondary.accent(limitingSurface, dark)
        val t = tertiary.accent(limitingSurface, dark)
        val pc = primary.tone(if (dark) 30f else 90f)
        val sc = secondary.tone(if (dark) 30f else 90f)
        val tc = tertiary.tone(if (dark) 30f else 90f)
        val inverseSurface = neutralSeed.tone(if (dark) 90f else 20f)

        // Fixed roles deliberately use identical tones in both modes.
        val pf = primary.tone(90f)
        val pfd = primary.tone(80f)
        val sf = secondary.tone(90f)
        val sfd = secondary.tone(80f)
        val tf = tertiary.tone(90f)
        val tfd = tertiary.tone(80f)

        // Use the complete constructor: no Material default accent can leak through.
        return ColorScheme(
            primary = p,
            onPrimary = neutral.inkOn(p),
            primaryContainer = pc,
            onPrimaryContainer = neutral.inkOn(pc),
            inversePrimary = primary.accent(inverseSurface, !dark),
            secondary = s,
            onSecondary = neutral.inkOn(s),
            secondaryContainer = sc,
            onSecondaryContainer = neutral.inkOn(sc),
            tertiary = t,
            onTertiary = neutral.inkOn(t),
            tertiaryContainer = tc,
            onTertiaryContainer = neutral.inkOn(tc),
            background = surface,
            onBackground = neutralSeed.tone(if (dark) 90f else 10f),
            surface = surface,
            onSurface = neutralSeed.tone(if (dark) 90f else 10f),
            surfaceVariant = variant,
            onSurfaceVariant = variantSeed.tone(if (dark) 80f else 30f),
            surfaceTint = neutral.tone(if (dark) 80f else 40f),
            inverseSurface = inverseSurface,
            inverseOnSurface = neutralSeed.tone(if (dark) 20f else 95f),
            error = if (dark) ErrorDark else ErrorLight,
            onError = if (dark) OnErrorDark else OnErrorLight,
            errorContainer = if (dark) ErrorContainerDark else ErrorContainerLight,
            onErrorContainer = if (dark) OnErrorContainerDark else OnErrorContainerLight,
            outline = variantSeed.tone(if (dark) 60f else 50f)
                .ensureContrast(limitingSurface, 3.05f, lighter = dark),
            outlineVariant = variantSeed.tone(if (dark) 35f else 80f),
            scrim = Color.Black,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = container,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            surfaceContainerLow = low,
            surfaceContainerLowest = lowest,
            primaryFixed = pf,
            primaryFixedDim = pfd,
            onPrimaryFixed = neutral.tone(10f),
            onPrimaryFixedVariant = neutral.tone(30f),
            secondaryFixed = sf,
            secondaryFixedDim = sfd,
            onSecondaryFixed = neutral.tone(10f),
            onSecondaryFixedVariant = neutral.tone(30f),
            tertiaryFixed = tf,
            tertiaryFixedDim = tfd,
            onTertiaryFixed = neutral.tone(10f),
            onTertiaryFixedVariant = neutral.tone(30f)
        )
    }
}

// Semantic error red is common to every palette, without palette harmonization.
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color.White
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private fun Color.accent(surface: Color, dark: Boolean): Color =
    ensureContrast(surface, 4.6f, lighter = dark)

private fun Color.inkOn(background: Color): Color {
    val useLight = background.luminance() < 0.179f
    return tone(if (useLight) 98f else 10f)
        .ensureContrast(background, 4.6f, lighter = useLight)
}

/** Preserve the original RGB until contrast requires the smallest black/white mix. */
private fun Color.ensureContrast(background: Color, ratio: Float, lighter: Boolean): Color {
    if (contrast(background) >= ratio) return this
    val end = if (lighter) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2f
        if (mix(end, mid).contrast(background) >= ratio) high = mid else low = mid
    }
    return mix(end, high)
}

/** CIE L* tone, reached on the source's RGB line toward black or white. */
private fun Color.tone(tone: Float): Color {
    val target = if (tone > 8f) ((tone + 16f) / 116f).pow(3) else tone / 903.3f
    val lighter = luminance() < target
    val end = if (lighter) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2f
        if ((mix(end, mid).luminance() < target) == lighter) low = mid else high = mid
    }
    return mix(end, high)
}

// Interpolate sRGB channels directly, retaining the sampled palette's RGB identity.
private fun Color.mix(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = 1f
)

private fun Color.contrast(other: Color): Float {
    val a = luminance()
    val b = other.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}
