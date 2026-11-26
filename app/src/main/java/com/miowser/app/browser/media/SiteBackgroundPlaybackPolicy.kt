package com.miowser.app.browser.media

import com.miowser.app.adblock.canonicalHostname
import java.net.URI

/** Defaults are configuration, not a claim that a service supports every WebView or subscription. */
internal object SiteBackgroundPlaybackPolicy {
    private val musicHosts = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "open.spotify.com", "play.spotify.com",
        "soundcloud.com", "www.soundcloud.com", "m.soundcloud.com",
        "music.apple.com", "beta.music.apple.com",
        "music.amazon.com", "music.amazon.in", "music.amazon.co.uk", "music.amazon.de",
        "music.amazon.fr", "music.amazon.it", "music.amazon.es", "music.amazon.co.jp",
        "music.amazon.ca", "music.amazon.com.au", "music.amazon.com.br",
        "deezer.com", "www.deezer.com", "listen.tidal.com", "tidal.com", "www.tidal.com",
        "pandora.com", "www.pandora.com", "jiosaavn.com", "www.jiosaavn.com",
        "gaana.com", "www.gaana.com", "audiomack.com", "www.audiomack.com",
        "play.qobuz.com", "open.qobuz.com", "tunein.com", "www.tunein.com",
        "iheart.com", "www.iheart.com"
    )

    fun host(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true) || uri.rawUserInfo != null) null
        else uri.host?.let(::canonicalHostname)
    }.getOrNull()

    fun defaultEnabled(host: String): Boolean {
        val canonical = canonicalHostname(host) ?: return false
        return canonical in musicHosts || canonical == "bandcamp.com" || canonical.endsWith(".bandcamp.com")
    }

    fun enabled(url: String, overrides: Map<String, Boolean>): Boolean {
        val host = host(url) ?: return false
        return overrides[host] ?: defaultEnabled(host)
    }

    fun decode(values: Set<String>): Map<String, Boolean> = values.mapNotNull { value ->
        val parts = value.split('|', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val host = canonicalHostname(parts[0]) ?: return@mapNotNull null
        val allowed = parts[1].toBooleanStrictOrNull() ?: return@mapNotNull null
        host to allowed
    }.toMap()

    fun encode(values: Map<String, Boolean>): Set<String> = values.map { "${it.key}|${it.value}" }.toSet()
}
