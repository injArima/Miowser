package com.miowser.app.adblock

/** Admission budgets, not performance guarantees. Reject whole lists, never truncate exceptions. */
internal object FilterLimits {
    const val SAVED_CUSTOM_LISTS = 50
    const val LIST_BYTES = 8 * 1024 * 1024
    const val MANUAL_BYTES = 256 * 1024
    const val RULES_PER_LIST = 120_000
    const val TOTAL_RULES = 250_000
    const val LINE_CHARS = 8_192
}

