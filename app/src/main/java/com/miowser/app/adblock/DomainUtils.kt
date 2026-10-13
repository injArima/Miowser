package com.miowser.app.adblock

import java.net.IDN
import java.util.Locale

internal fun canonicalHostname(raw: String): String? {
    val host = raw.trim().removeSuffix(".").removePrefix("[").removeSuffix("]")
    if (host.isEmpty()) return null
    if (':' in host) return host.lowercase(Locale.ROOT)
    return runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
        .getOrNull()?.takeIf { it.length <= 253 && it.split('.').none(String::isEmpty) }
}

fun hostnameMatchesDomain(hostname: String, domain: String): Boolean =
    hostname == domain || hostname.endsWith(".$domain")

fun isSameRegistrableDomain(hostA: String, hostB: String): Boolean {
    val a = canonicalHostname(hostA) ?: return false
    val b = canonicalHostname(hostB) ?: return false
    return a == b || PublicSuffixDomains.site(a) == PublicSuffixDomains.site(b)
}

/** Bundled Mozilla PSL, including ICANN and PRIVATE rules; no runtime network request. */
internal object PublicSuffixDomains {
    private data class Rules(val exact: Set<String>, val wildcard: Set<String>, val exceptions: Set<String>)
    private val cache = BoundedFilterCache<String, String>(2_048)
    private val rules: Rules by lazy {
        val exact = HashSet<String>()
        val wildcard = HashSet<String>()
        val exceptions = HashSet<String>()
        val stream = checkNotNull(PublicSuffixDomains::class.java.getResourceAsStream("/public_suffix_list.dat")) {
            "Bundled Public Suffix List is missing"
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val token = line.trim().takeWhile { !it.isWhitespace() }
                if (token.isNotEmpty() && !token.startsWith("//")) {
                    val destination = when {
                        token.startsWith("!") -> exceptions
                        token.startsWith("*.") -> wildcard
                        else -> exact
                    }
                    canonicalHostname(token.removePrefix("!").removePrefix("*."))?.let(destination::add)
                }
            }
        }
        check(exact.isNotEmpty()) { "Bundled Public Suffix List is empty" }
        Rules(exact, wildcard, exceptions)
    }
    fun warmUp() { rules }
    fun site(host: String): String {
        if (':' in host || host.all { it.isDigit() || it == '.' }) return host
        cache.get(host)?.let { return it }
        val labels = host.split('.')
        val loaded = rules
        var suffixLength = 1
        for (index in labels.indices) {
            val suffix = labels.subList(index, labels.size).joinToString(".")
            val count = labels.size - index
            if (suffix in loaded.exceptions) { suffixLength = count - 1; break }
            if (suffix in loaded.exact) suffixLength = maxOf(suffixLength, count)
            if (index > 0 && suffix in loaded.wildcard) suffixLength = maxOf(suffixLength, count + 1)
        }
        val result = labels.takeLast((suffixLength + 1).coerceAtMost(labels.size)).joinToString(".")
        cache.put(host, result)
        return result
    }
}
