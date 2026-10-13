package com.miowser.app.library

import java.util.Locale

fun baselineWebsiteRule(folder: LibraryFolder, facts: LibraryRuleFacts): Boolean {
    val domains = folder.websiteDomains
    if (folder.titleRule != null || domains.isEmpty()) return false
    val matches = domains.map { domain ->
        libraryDomainMatches(facts.pageUrl, domain) || libraryDomainMatches(facts.mediaUrl, domain)
    }
    return when (folder.websiteOperator) {
        LibraryRuleOperator.ALL -> matches.all { it }
        LibraryRuleOperator.ANY -> matches.any { it }
    }
}

private fun score(folder: LibraryFolder, facts: LibraryRuleFacts): Pair<Int, Int>? {
    if (baselineWebsiteRule(folder, facts)) {
        return 2 to folder.websiteDomains.filter { domain ->
            libraryDomainMatches(facts.pageUrl, domain) || libraryDomainMatches(facts.mediaUrl, domain)
        }.maxOf(String::length)
    }
    val titleRule = folder.titleRule ?: return null
    if (!baselineTitleRule(titleRule, facts.mediaTitle, facts.pageTitle)) return null
    return 1 to if (titleRule.operator == LibraryRuleOperator.ALL) titleRule.phrases.size else 0
}

/** A root rule takes from the default folder; each child can take only from its parent. */
fun baselineRouteLibraryVideo(
    folders: List<LibraryFolder>,
    defaultFolderId: String?,
    sourceFolderId: String?,
    facts: LibraryRuleFacts,
    allowRootRouting: Boolean = true
): String? {
    var current = sourceFolderId
    if (allowRootRouting && current == defaultFolderId) {
        val roots = folders.filter { it.parentId == null && it.id != defaultFolderId }
        current = bestRuleMatch(roots, facts)?.id ?: current
    }
    val visited = mutableSetOf<String?>()
    while (visited.add(current)) {
        val children = folders.filter { it.parentId == current && current != null }
        current = bestRuleMatch(children, facts)?.id ?: break
    }
    return current
}

private fun bestRuleMatch(folders: List<LibraryFolder>, facts: LibraryRuleFacts): LibraryFolder? {
    var winner: LibraryFolder? = null
    var best = 0 to 0
    folders.forEach { folder ->
        val candidate = score(folder, facts) ?: return@forEach
        if (winner == null || candidate.first > best.first ||
            (candidate.first == best.first && candidate.second > best.second)) {
            winner = folder
            best = candidate
        }
    }
    return winner
}

private val baselineWords = Regex("[\\p{L}\\p{M}\\p{N}]+")

private fun baselineTokens(value: String?): List<String> = value.orEmpty()
    .lowercase(Locale.ROOT)
    .let { baselineWords.findAll(it).map(MatchResult::value).toList() }

private fun baselineContains(title: List<String>, phrase: List<String>): Boolean =
    phrase.isNotEmpty() && title.windowed(phrase.size).any { it == phrase }

fun baselineTitleRule(rule: LibraryTitleRule, mediaTitle: String?, pageTitle: String?): Boolean {
    val titles = when (rule.source) {
        LibraryTitleSource.MEDIA -> listOf(baselineTokens(mediaTitle))
        LibraryTitleSource.PAGE -> listOf(baselineTokens(pageTitle))
        LibraryTitleSource.COMBINED -> listOf(baselineTokens(mediaTitle), baselineTokens(pageTitle))
    }
    val phrases = rule.phrases.map(::baselineTokens).filter(List<String>::isNotEmpty)
    if (phrases.isEmpty()) return false
    val matches = phrases.map { phrase -> titles.any { baselineContains(it, phrase) } }
    return when (rule.operator) {
        LibraryRuleOperator.ALL -> matches.all { it }
        LibraryRuleOperator.ANY -> matches.any { it }
    }
}
