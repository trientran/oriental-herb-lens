package com.uri.lee.dl.core.common.text

/**
 * Folds text for diacritic-insensitive matching: lowercase, accented Latin letters (including all
 * Vietnamese vowel forms and đ) reduced to their base letter, punctuation turned into spaces, and
 * combining marks dropped. Pure Kotlin, so it behaves the same on every platform.
 *
 * Precomposed input folds one character to one character, so a match position in the folded text
 * is also its position in the original. [FoldedText.sourceIndex] keeps that mapping when the input
 * contained combining marks.
 */
object VietnameseText {

    /** Accented lowercase letters and, at the same index in [BASES], their base letter. */
    private const val ACCENTED =
        "àáâãäåçèéêëìíîïñòóôõöùúûüýÿāăąćĉċčďēĕėęěĝğġģĥĩīĭ" +
        "įĵķĺļľńņňōŏőŕŗřśŝşšţťũūŭůűųŵŷźżžơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭ" +
        "ǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳḁḃḅḇḉḋḍḏḑḓḕḗḙḛḝḟḡḣḥḧḩḫ" +
        "ḭḯḱḳḵḷḹḻḽḿṁṃṅṇṉṋṍṏṑṓṕṗṙṛṝṟṡṣṥṧṩṫṭṯṱṳṵṷṹṻṽṿẁẃẅẇẉẋ" +
        "ẍẏẑẓẕẖẗẘẙạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừử" +
        "ữựỳỵỷỹđðøłßæœ"

    private const val BASES =
        "aaaaaaceeeeiiiinooooouuuuyyaaaccccdeeeeegggghiii" +
        "ijklllnnnooorrrssssttuuuuuuwyzzzouaiouuuuuaagkoo" +
        "jgnaaaeeiioorruusthaeooooyabbbcdddddeeeeefghhhhh" +
        "iikkkllllmmmnnnnoooopprrrrsssssttttuuuuuvvwwwwwx" +
        "xyzzzhtwyaaaaaaaaaaaaeeeeeeeeiioooooooooooouuuuu" +
        "uuyyyyddolsao"

    private val foldMap: Map<Char, Char> = ACCENTED.zip(BASES).toMap()

    fun fold(text: String): String = foldWithIndex(text).folded

    fun foldWithIndex(text: String): FoldedText {
        val out = StringBuilder(text.length)
        val sourceIndex = IntArray(text.length)
        var n = 0
        for ((i, raw) in text.withIndex()) {
            if (raw in '\u0300'..'\u036F') continue // combining marks (decomposed input)
            val lower = raw.lowercaseChar()
            val c = foldMap[lower] ?: lower
            out.append(if (c.isLetterOrDigit()) c else ' ')
            sourceIndex[n++] = i
        }
        return FoldedText(out.toString(), sourceIndex.copyOf(n))
    }

    /** Folded, with runs of spaces collapsed and ends trimmed; the form queries are compared in. */
    fun normalizeQuery(text: String): String = fold(text).split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}

/** [folded] text, where character `i` came from `original[sourceIndex[i]]`. */
class FoldedText(val folded: String, val sourceIndex: IntArray) {
    /** Maps a range in [folded] back to the original text. */
    fun toSourceRange(range: IntRange): IntRange = sourceIndex[range.first]..sourceIndex[range.last]
}
