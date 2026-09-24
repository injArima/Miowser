package com.miowser.app.grabber

import java.net.URI

data class StaticPageLinks(
    val mediaUrls: List<String>,
    val pageUrls: List<String>,
    val mediaTypes: Map<String, MediaType>
)

/** Reads URL-bearing HTML attributes without executing page scripts. Network results still need MediaProbe. */
object StaticMediaHtmlParser {
    private val tags = Regex("<\\s*(video|audio|source|track|a|iframe|embed)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attributes = Regex("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val numericEntity = Regex("&#(x[0-9a-fA-F]+|[0-9]+);?")
    private val ignoredBlocks = Regex("<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(html: String, pageUrl: String, maxLinks: Int = 256): StaticPageLinks {
        val media = linkedMapOf<String, MediaType>()
        val pages = linkedSetOf<String>()
        for (tag in tags.findAll(ignoredBlocks.replace(html.take(512 * 1024), ""))) {
            if (media.size + pages.size >= maxLinks) break
            val kind = tag.groupValues[1].lowercase()
            val attrs = attributes.findAll(tag.value).associate {
                it.groupValues[1].lowercase() to decodeHtml(it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty())
            }
            val raw = when (kind) {
                "a", "iframe" -> attrs["href"] ?: attrs["src"]
                else -> attrs["src"] ?: attrs["data-src"]
            } ?: continue
            val resolved = resolve(pageUrl, raw) ?: continue
            val type = MediaUrlClassifier.classify(resolved)
                ?: if (kind == "source" || kind == "video" || kind == "audio")
                    MediaUrlClassifier.classifyByMimeType(attrs["type"]) else null
            if (type != null) media[resolved] = type
            else if (kind == "iframe" || (kind == "a" && looksLikeMediaPage(resolved))) pages.add(resolved)
        }
        return StaticPageLinks(media.keys.toList(), pages.toList(), media)
    }

    private fun resolve(base: String, raw: String): String? = runCatching {
        val uri = URI(base).resolve(raw.trim()).normalize()
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return null
        // Keep existing %-encoded signature bytes intact. Rebuilding URI from raw components
        // would encode '%' a second time and break expiring media links.
        uri.toASCIIString().substringBefore('#')
    }.getOrNull()

    // A recursive scan never follows every link on a site (GET logout/delete links exist).
    // It follows embeds and obvious media-detail routes only.
    private fun looksLikeMediaPage(url: String): Boolean {
        val path = URI(url).path.orEmpty().lowercase()
        return path.split('/').any { segment ->
            segment in setOf("v", "e", "watch", "video", "videos", "embed", "player",
                "episode", "episodes", "movie", "movies", "stream") ||
                segment.startsWith("watch-") || segment.startsWith("episode-")
        }
    }

    private fun decodeHtml(raw: String): String = numericEntity.replace(
        raw.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    ) { match ->
        val value = match.groupValues[1]
        val point = if (value.startsWith("x", true)) value.drop(1).toIntOrNull(16) else value.toIntOrNull()
        point?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
    }
}
