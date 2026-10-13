package com.miowser.app.adblock

/**
 * Small always-on backup list, active even if the bundled/subscribed EasyList asset fails to
 * load or parse for some reason — matches 1DM's own "curated always-on fallback list"
 * belt-and-suspenders pattern (see project memory: 1dm-adblock-engine.md). These are
 * plain `||domain^` rules, parsed the same way as any other network rule.
 */
object CuratedFallbackList {
    val RULES = listOf(
        "||doubleclick.net^",
        "||googlesyndication.com^",
        "||google-analytics.com^",
        "||googletagmanager.com^",
        "||googletagservices.com^",
        "||adservice.google.com^",
        "||adnxs.com^",
        "||scorecardresearch.com^",
        "||outbrain.com^",
        "||taboola.com^",
        "||criteo.com^",
        "||moatads.com^",
        "||amazon-adsystem.com^",
        "||connect.facebook.net^",
        "||pubmatic.com^",
        "||rubiconproject.com^",
        "||openx.net^",
        "||quantserve.com^",
        "||adform.net^",
        "||casalemedia.com^"
    )
}
