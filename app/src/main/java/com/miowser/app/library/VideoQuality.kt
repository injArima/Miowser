package com.miowser.app.library

/** The short edge expresses vertical resolution for both landscape and portrait videos. */
fun videoQualityHeight(width: Int, height: Int): Int? = when {
    width > 0 && height > 0 -> minOf(width, height)
    height > 0 -> height
    else -> null
}

fun videoQualityLabel(height: Int?): String? = height?.takeIf { it > 0 }?.let { "${it}p" }
