package com.miowser.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MiowserBlueDark,
    onPrimary = Color(0xFF0D2B64),
    primaryContainer = MiowserBlueContainerDark,
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = MiowserTealDark,
    onSecondary = Color(0xFF003542),
    secondaryContainer = MiowserTealContainerDark,
    onSecondaryContainer = Color(0xFFBCEAF8),
    tertiary = MiowserWarmDark,
    onTertiary = Color(0xFF442951),
    tertiaryContainer = MiowserWarmContainerDark,
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF10131B),
    onBackground = Color(0xFFE1E2EE),
    surface = Color(0xFF10131B),
    onSurface = Color(0xFFE1E2EE),
    surfaceDim = Color(0xFF10131B),
    surfaceBright = Color(0xFF363941),
    surfaceContainerLowest = Color(0xFF0B0F17),
    surfaceContainerLow = Color(0xFF191D25),
    surfaceContainer = Color(0xFF1D2129),
    surfaceContainerHigh = Color(0xFF282C34),
    surfaceContainerHighest = Color(0xFF333740),
    surfaceVariant = Color(0xFF434750),
    onSurfaceVariant = Color(0xFFC3C6D1),
    outline = Color(0xFF8D909B)
)

private val LightColorScheme = lightColorScheme(
    primary = MiowserBlue,
    onPrimary = Color.White,
    primaryContainer = MiowserBlueContainer,
    onPrimaryContainer = Color(0xFF112E6C),
    secondary = MiowserTeal,
    onSecondary = Color.White,
    secondaryContainer = MiowserTealContainer,
    onSecondaryContainer = Color(0xFF003543),
    tertiary = MiowserWarm,
    onTertiary = Color.White,
    tertiaryContainer = MiowserWarmContainer,
    onTertiaryContainer = Color(0xFF432653),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191B23),
    surfaceDim = Color(0xFFD8DAE3),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3FA),
    surfaceContainer = Color(0xFFEBEDF5),
    surfaceContainerHigh = Color(0xFFE5E7EF),
    surfaceContainerHighest = Color(0xFFDFE1E9),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF434750),
    outline = Color(0xFF737782)
)

private val PlayerDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8)
)
private val PlayerLightColorScheme = lightColorScheme(
    primary = Color(0xFF6650A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260)
)

/**
 * Dynamic wallpaper color remains the first choice; these fallbacks cover older devices.
 */
@Composable
fun MiowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}

/** Restores the player's pre-migration typography, shapes, and fallback palette. */
@Composable
fun MiowserPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PlayerDarkColorScheme
        else -> PlayerLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlayerTypography,
        shapes = Shapes(),
        content = content
    )
}
