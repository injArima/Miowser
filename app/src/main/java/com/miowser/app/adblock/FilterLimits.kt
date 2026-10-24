package com.miowser.app.adblock

/** Byte and line limits guard malformed downloads; rule counts do not reject valid lists. */
internal object FilterLimits {
    const val SAVED_CUSTOM_LISTS = 50
    const val LIST_BYTES = 32 * 1024 * 1024
    const val MANUAL_BYTES = 256 * 1024
    const val LINE_CHARS = 8_192
}

