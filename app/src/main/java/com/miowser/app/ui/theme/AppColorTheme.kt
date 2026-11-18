package com.miowser.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Stable preference IDs and unmodified reference-image colors, in reference order. */
enum class AppColorTheme(val label: String, val swatches: List<Color>) {
    // Wallpaper has no static source swatches; its preview is supplied by the system.
    WALLPAPER("Wallpaper", emptyList()),
    MIOWSER("Miowser", listOf(MiowserBlue, MiowserTeal, MiowserWarm)),
    SEA("Sea", swatches(0x0F2854, 0x1C4D8D, 0x4988C4, 0xBDE8F5)),
    AMBER("Amber", swatches(
        0x03071E, 0x370617, 0x6A040F, 0x9D0208, 0xD00000,
        0xDC2F02, 0xE85D04, 0xF48C06, 0xFAA307, 0xFFBA08
    )),
    BLACK_GOLD("Black Gold", swatches(0x000000, 0x14213D, 0xFCA311, 0xE5E5E5, 0xFFFFFF)),
    SUMMER_OCEAN("Summer Ocean", swatches(0xE63946, 0xF1FAEE, 0xA8DADC, 0x457B9D, 0x1D3557)),
    HALLOWEEN("Halloween", swatches(0x000000, 0x830000, 0xBC0202, 0xFF0000)),
    RETRO("Retro", swatches(0x15370D, 0x728E6D, 0xE5CD82, 0xFFF2B4)),
    SUNSET("Sunset", swatches(0x155263, 0xFF6F3C, 0xFF9A3C, 0xFFC93C)),
    SPACE_MAN("Space Man", swatches(0xFF204E, 0xA0153E, 0x5D0E41, 0x00224D)),
    WOOD("Wood", swatches(0x2D2424, 0x5C3D2E, 0xB85C38, 0xE0C097)),
    COTTON_CANDY("Cotton Candy", swatches(0xCDF0EA, 0xF9F9F9, 0xF7DBF0, 0xBEAEE2)),
    GRASS("Grass", swatches(0xE5D9B6, 0xA4BE7B, 0x5F8D4E, 0x285430)),
    ICE_CREAM("Ice Cream", swatches(0xDF301C, 0xFF9100, 0xFFF1D1, 0x00B7CD)),
    NIGHT("Night", swatches(0x091540, 0x1B2CC1, 0x7692FF, 0xABD2FA)),
    UMBRELLA("Umbrella", swatches(0x2391FF, 0xFFF83F, 0xFFCD54, 0xF70063))
}

private fun swatches(vararg rgb: Int): List<Color> = rgb.map { Color(it or 0xFF000000.toInt()) }
