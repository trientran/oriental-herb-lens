package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.core.common.text.TextNormalizer
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.Taxonomy

internal class CatalogFormatException(message: String) : Exception(message)

internal data class CatalogReadResult(
    val species: List<Species>,
    /** 1-based line numbers of data rows that were skipped as malformed. */
    val skippedRows: List<Int>,
)

/**
 * Reads the species catalog CSV (a GBIF species export plus a `vietnameseName` column).
 * Only the columns the app uses are read; any extra columns are ignored. The [Taxonomy] columns are
 * optional: a catalog without them reads as before.
 */
internal class SpeciesCsvReader(private val normalizer: TextNormalizer) {

    fun read(text: String): CatalogReadResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) throw CatalogFormatException("Catalog is empty")
        val header = rows.first().map { it.trim() }
        val missing = REQUIRED_COLUMNS - header.toSet()
        if (missing.isNotEmpty()) throw CatalogFormatException("Catalog is missing columns: $missing")
        val column = header.withIndex().associate { (index, name) -> name to index }

        val species = mutableListOf<Species>()
        val skipped = mutableListOf<Int>()
        rows.drop(1).forEachIndexed { index, row ->
            val id = row.getOrNull(column.getValue(SPECIES_KEY))?.trim()?.toLongOrNull()
            val scientificName = row.getOrNull(column.getValue(CANONICAL_NAME))?.let(::clean).orEmpty()
            if (row.size != header.size || id == null || scientificName.isEmpty()) {
                skipped += index + 2 // + header row, 1-based
                return@forEachIndexed
            }
            fun value(name: String) = clean(row[column.getValue(name)])
            fun optional(name: String) = column[name]?.let { clean(row[it]) }.orEmpty()
            species += Species(
                id = id,
                scientificName = scientificName,
                authorship = value(AUTHORSHIP),
                family = value(FAMILY),
                genus = value(GENUS),
                vietnameseNames = splitNames(value(VIETNAMESE_NAME)),
                englishNames = splitNames(value(VERNACULAR_NAME)),
                taxonomy = Taxonomy(
                    kingdom = optional("kingdom"),
                    phylum = optional("phylum"),
                    className = optional("class"),
                    order = optional("order"),
                    rank = optional("rank"),
                    status = optional("taxonomicStatus"),
                    publishedIn = optional("publishedIn"),
                    basionym = optional("basionym"),
                ),
            )
        }
        return CatalogReadResult(species, skipped)
    }

    private fun clean(value: String) = normalizer.toNfc(value).trim()

    companion object {
        const val SPECIES_KEY = "speciesKey"
        const val CANONICAL_NAME = "canonicalName"
        const val AUTHORSHIP = "authorship"
        const val FAMILY = "family"
        const val GENUS = "genus"
        const val VIETNAMESE_NAME = "vietnameseName"
        const val VERNACULAR_NAME = "vernacularName"

        val REQUIRED_COLUMNS = setOf(
            SPECIES_KEY, CANONICAL_NAME, AUTHORSHIP, FAMILY, GENUS, VIETNAMESE_NAME, VERNACULAR_NAME,
        )

        /**
         * Name lists in the source data use both `;` and `,` as separators. Separators inside
         * parentheses don't split, so "Mằm sâm đăm (Tày)" and "X (a, b)" each stay one name.
         * Keeps the original order (the first name is the preferred one) and drops
         * case-insensitive duplicates.
         */
        internal fun splitNames(raw: String): List<String> {
            val names = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            for (c in raw) {
                when {
                    c == '(' -> { depth++; current.append(c) }
                    c == ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(c) }
                    (c == ';' || c == ',') && depth == 0 -> { names += current.toString(); current.clear() }
                    else -> current.append(c)
                }
            }
            names += current.toString()
            return names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        }
    }
}
