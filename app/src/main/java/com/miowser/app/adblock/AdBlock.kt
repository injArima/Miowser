package com.miowser.app.adblock

/**
 * App-wide singleton holding the one [AdBlockEngine] instance. Revisit with real DI (Hilt or
 * similar) once the architecture has more than this one thing to wire up — premature module
 * boundaries were exactly the kind of over-engineering the retro flagged as not worth doing
 * before there's real code to bound.
 */
object AdBlock {
    val engine = AdBlockEngine()
}
