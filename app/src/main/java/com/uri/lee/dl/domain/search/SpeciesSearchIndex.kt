package com.uri.lee.dl.domain.search

import com.uri.lee.dl.core.common.text.FoldedText
import com.uri.lee.dl.core.common.text.VietnameseText
import com.uri.lee.dl.domain.model.Species

enum class NameKind { VIETNAMESE, SCIENTIFIC, ENGLISH }

/** A species that matched, with the name that matched best and where the match is in that name. */
data class SpeciesMatch(
    val species: Species,
    val matchedName: String,
    val kind: NameKind,
    /** Characters of [matchedName] to highlight, or null when the match is fuzzy. */
    val highlight: IntRange?,
    val score: Int,
)

/**
 * Diacritic-insensitive search over every name of every species: "dinh lang", "ĐINH LĂNG" and
 * "lang dinh" all find Đinh lăng, and one typo per word is forgiven (in words of 4+ letters, keeping
 * the first letter).
 * The whole catalog is a few thousand species, so it's searched in memory.
 */
class SpeciesSearchIndex(species: List<Species>) {

    private class Name(val text: String, val kind: NameKind, val preferred: Boolean) {
        val folded: FoldedText = VietnameseText.foldWithIndex(text)
        val words: List<IntRange> = wordRanges(folded.folded)
    }

    private class Entry(val species: Species, val names: List<Name>)

    private val entries: List<Entry> = species.map { s ->
        Entry(
            s,
            buildList {
                s.vietnameseNames.forEachIndexed { i, n -> add(Name(n, NameKind.VIETNAMESE, preferred = i == 0)) }
                add(Name(s.scientificName, NameKind.SCIENTIFIC, preferred = true))
                s.englishNames.forEachIndexed { i, n -> add(Name(n, NameKind.ENGLISH, preferred = i == 0)) }
            },
        )
    }

    fun search(query: String, limit: Int = 50): List<SpeciesMatch> {
        val q = VietnameseText.normalizeQuery(query)
        if (q.isEmpty()) return emptyList()
        val tokens = q.split(' ')
        return entries
            .mapNotNull { entry -> entry.names.mapNotNull { match(entry.species, it, q, tokens) }.maxByOrNull { it.score } }
            .sortedWith(
                compareByDescending<SpeciesMatch> { it.score }
                    .thenBy { it.matchedName.length }
                    .thenBy { it.species.scientificName },
            )
            .take(limit)
    }

    private fun match(species: Species, name: Name, q: String, tokens: List<String>): SpeciesMatch? {
        val folded = name.folded.folded
        val collapsed = folded.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        val words = name.words.map { folded.substring(it) }

        val (tier, range) = when {
            collapsed == q -> EXACT to (name.words.first().first..name.words.last().last)
            folded.startsWith(q) -> PREFIX to (0 until q.length)
            tokens.all { t -> words.any { it.startsWith(t) } } -> {
                val first = name.words[words.indexOfFirst { it.startsWith(tokens.first()) }]
                val inOrder = isInOrder(tokens, words)
                (if (inOrder) WORDS_IN_ORDER else WORDS_ANY_ORDER) to (first.first until first.first + tokens.first().length)
            }
            folded.contains(q) -> folded.indexOf(q).let { SUBSTRING to (it until it + q.length) }
            else -> fuzzyScore(tokens, words)?.let { it to null } ?: return null
        }
        val score = tier + when (name.kind) {
            NameKind.VIETNAMESE -> if (name.preferred) 30 else 20
            NameKind.SCIENTIFIC -> 15
            NameKind.ENGLISH -> if (name.preferred) 10 else 5
        }
        return SpeciesMatch(species, name.text, name.kind, range?.let(name.folded::toSourceRange), score)
    }

    /**
     * Every query word must match a different word of the name, by its start or within one edit
     * (words of 4+ letters). Exact starts score higher, so "dinh lanh" prefers "Đinh lăng" (one
     * typo) over "Linh" (both words typo-matching the same word).
     */
    private fun fuzzyScore(tokens: List<String>, words: List<String>): Int? {
        val used = BooleanArray(words.size)
        var exactStarts = 0
        for (t in tokens) {
            val exact = words.indices.firstOrNull { !used[it] && words[it].startsWith(t) }
            val i = exact ?: words.indices.firstOrNull { !used[it] && isTypoOf(t, words[it]) } ?: return null
            used[i] = true
            if (exact != null) exactStarts++
        }
        return FUZZY + 20 * exactStarts
    }

    /** One edit away, in a word of 4+ letters, keeping the first letter (people rarely mistype it). */
    private fun isTypoOf(token: String, word: String) =
        token.length >= 4 && word.isNotEmpty() && token[0] == word[0] && withinOneEdit(token, word)

    private fun isInOrder(tokens: List<String>, words: List<String>): Boolean {
        var from = 0
        for (t in tokens) {
            val i = (from until words.size).firstOrNull { words[it].startsWith(t) } ?: return false
            from = i + 1
        }
        return true
    }

    companion object {
        private const val EXACT = 1000
        private const val PREFIX = 800
        private const val WORDS_IN_ORDER = 650
        private const val WORDS_ANY_ORDER = 600
        private const val SUBSTRING = 400
        private const val FUZZY = 200

        private fun wordRanges(folded: String): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var start = -1
            for (i in folded.indices) {
                if (folded[i] != ' ' && start < 0) start = i
                if (folded[i] == ' ' && start >= 0) { ranges += start until i; start = -1 }
            }
            if (start >= 0) ranges += start until folded.length
            return ranges.ifEmpty { listOf(0 until folded.length) }
        }

        /** True if [a] and [b] differ by at most one insertion, deletion, substitution or swap. */
        internal fun withinOneEdit(a: String, b: String): Boolean {
            if (a == b) return true
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0
            while (i < a.length && i < b.length && a[i] == b[i]) i++
            return when {
                a.length == b.length ->
                    a.substring(i + 1) == b.substring(i + 1) ||
                        (i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2))
                a.length > b.length -> a.substring(i + 1) == b.substring(i)
                else -> a.substring(i) == b.substring(i + 1)
            }
        }
    }
}
