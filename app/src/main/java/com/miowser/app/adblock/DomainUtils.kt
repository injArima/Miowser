package com.miowser.app.adblock

/**
 * Approximate registrable-domain comparison: compares the last two dot-separated labels
 * (e.g. "sub.example.com" and "example.com" both reduce to "example.com"). This is NOT a real
 * public-suffix-list lookup, so multi-part TLDs like "co.uk" are handled incorrectly
 * ("foo.co.uk" and "bar.co.uk" are treated as the same registrable domain when they aren't) —
 * a deliberate, documented simplification rather than pulling in a full PSL for v1.
 */
fun isSameRegistrableDomain(hostA: String, hostB: String): Boolean =
    lastTwoLabels(hostA) == lastTwoLabels(hostB)

/**
 * Real domain-hierarchy matching (not the registrable-domain approximation above): true if
 * [hostname] is exactly [domain], or a subdomain of it. Shared by network-rule `domain=`/
 * `domain=~` matching (NetworkFilterRule) and cosmetic-rule domain inclusion/exclusion
 * (AdBlockEngine) — both EasyList features use the identical "this domain or any subdomain"
 * rule, just applied to different things (page hostname vs. the domain list on a `$domain=`
 * option or a `domain##selector` line).
 */
fun hostnameMatchesDomain(hostname: String, domain: String): Boolean =
    hostname == domain || hostname.endsWith(".$domain")

private fun lastTwoLabels(host: String): String {
    val parts = host.lowercase().split('.')
    return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
}
