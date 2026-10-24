package com.miowser.app.library

import java.util.Locale

enum class LibraryTitleSource { MEDIA, PAGE, COMBINED }
enum class LibraryRuleOperator { ALL, ANY }

/** A folder's one title rule: all phrases or any phrase, never a mixed expression. */
data class LibraryTitleRule(
    val source: LibraryTitleSource,
    val operator: LibraryRuleOperator,
    val phrases: List<String>
)

private val titleWords = Regex("[\\p{L}\\p{M}\\p{N}]+")

internal fun titleTokens(value: String?): List<String> = value.orEmpty()
    .lowercase(Locale.ROOT)
    .let { titleWords.findAll(it).map(MatchResult::value).toList() }

internal fun containsPhrase(title: List<String>, phrase: List<String>): Boolean {
    if (phrase.isEmpty() || phrase.size > title.size) return false
    for (start in 0..title.size - phrase.size) {
        var offset = 0
        while (offset < phrase.size && title[start + offset] == phrase[offset]) offset++
        if (offset == phrase.size) return true
    }
    return false
}

internal class CompiledTitleRule(val rule: LibraryTitleRule) {
    private val phrases = rule.phrases.map(::titleTokens).filter { it.isNotEmpty() }
    fun matches(media: List<String>, page: List<String>): Boolean {
        if (phrases.isEmpty()) return false
        fun matchesPhrase(phrase: List<String>): Boolean = when (rule.source) {
            LibraryTitleSource.MEDIA -> containsPhrase(media, phrase)
            LibraryTitleSource.PAGE -> containsPhrase(page, phrase)
            LibraryTitleSource.COMBINED -> containsPhrase(media, phrase) || containsPhrase(page, phrase)
        }
        return when (rule.operator) {
            LibraryRuleOperator.ALL -> phrases.all(::matchesPhrase)
            LibraryRuleOperator.ANY -> phrases.any(::matchesPhrase)
        }
    }
}

fun matchesLibraryTitleRule(rule: LibraryTitleRule, mediaTitle: String?, pageTitle: String?): Boolean =
    CompiledTitleRule(rule).matches(titleTokens(mediaTitle), titleTokens(pageTitle))
