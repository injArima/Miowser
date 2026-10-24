package com.miowser.app.library

data class LibraryRuleFacts(
    val pageUrl: String?,
    val mediaUrl: String?,
    val mediaTitle: String?,
    val pageTitle: String?
)

fun LibraryEntry.ruleFacts(): LibraryRuleFacts = LibraryRuleFacts(
    pageUrl = pageUrl,
    mediaUrl = mediaUri,
    mediaTitle = videoName?.takeIf(String::isNotBlank) ?: title,
    pageTitle = pageTitle
)

fun matchesLibraryWebsiteRule(folder: LibraryFolder, facts: LibraryRuleFacts): Boolean {
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

/** Immutable rule index, rebuilt only when folder rules/defaults change. */
class CompiledLibraryRouter(folders: List<LibraryFolder>, private val defaultFolderId: String?) {
    private data class Rule(val folder: LibraryFolder, val domains: List<String>, val title: CompiledTitleRule?)
    private val indexed = folders.map { Rule(it, it.websiteDomains, it.titleRule?.let(::CompiledTitleRule)) }
    private val needsMedia = indexed.any { it.title?.rule?.source in listOf(LibraryTitleSource.MEDIA, LibraryTitleSource.COMBINED) }
    private val needsPage = indexed.any { it.title?.rule?.source in listOf(LibraryTitleSource.PAGE, LibraryTitleSource.COMBINED) }
    private val hasRules = indexed.any { it.title != null || it.domains.isNotEmpty() }
    private val roots = indexed.filter { it.folder.parentId == null && it.folder.id != defaultFolderId }
    private val children = indexed.filter { it.folder.parentId != null }.groupBy { it.folder.parentId }

    fun route(sourceFolderId: String?, facts: LibraryRuleFacts, allowRootRouting: Boolean = true): String? {
        // Parse/tokenize each input once, irrespective of the number/depth of rules.
        if (!hasRules) return sourceFolderId
        val media = if (needsMedia) titleTokens(facts.mediaTitle) else emptyList()
        val page = if (needsPage) titleTokens(facts.pageTitle) else emptyList()
        val pageHost = libraryDomain(facts.pageUrl)
        val mediaHost = libraryDomain(facts.mediaUrl)
        fun domainMatches(domain: String) =
            (pageHost != null && (pageHost == domain || pageHost.endsWith(".$domain"))) ||
                (mediaHost != null && (mediaHost == domain || mediaHost.endsWith(".$domain")))
        fun best(candidates: List<Rule>?): String? {
            var winner: String? = null
            var bestTier = 0
            var bestSpecificity = 0
            candidates?.forEach { candidate ->
                var tier = 0
                var specificity = 0
                if (candidate.title != null) {
                    if (!candidate.title.matches(media, page)) return@forEach
                    tier = 1
                    specificity = if (candidate.title.rule.operator == LibraryRuleOperator.ALL)
                        candidate.title.rule.phrases.size else 0
                } else if (candidate.domains.isNotEmpty()) {
                    val matches = if (candidate.folder.websiteOperator == LibraryRuleOperator.ALL)
                        candidate.domains.all(::domainMatches) else candidate.domains.any(::domainMatches)
                    if (!matches) return@forEach
                    tier = 2
                    specificity = candidate.domains.filter(::domainMatches).maxOf(String::length)
                } else return@forEach
                if (winner == null || tier > bestTier || (tier == bestTier && specificity > bestSpecificity)) {
                    winner = candidate.folder.id; bestTier = tier; bestSpecificity = specificity
                }
            }
            return winner
        }
        var current = sourceFolderId
        if (allowRootRouting && current == defaultFolderId) current = best(roots) ?: current
        val visited = mutableSetOf<String?>()
        while (visited.add(current)) current = best(children[current]) ?: break
        return current
    }
}

/** A root takes from default; each child can take only from its parent. */
fun routeLibraryVideo(folders: List<LibraryFolder>, defaultFolderId: String?, sourceFolderId: String?,
    facts: LibraryRuleFacts, allowRootRouting: Boolean = true): String? =
    CompiledLibraryRouter(folders, defaultFolderId).route(sourceFolderId, facts, allowRootRouting)

/** Computation never reads the live Compose lists. */
fun planLibraryReorganization(records: List<LibraryEntry>, router: CompiledLibraryRouter,
    sourceId: String?, childRule: Boolean): Map<String, String?> = buildMap {
    records.forEach { entry ->
        if (!entry.manualFolder && entry.folderId == sourceId) {
            val destination = router.route(sourceId, entry.ruleFacts(), allowRootRouting = !childRule)
            if (destination != sourceId) put(entry.identityKey, destination)
        }
    }
}
