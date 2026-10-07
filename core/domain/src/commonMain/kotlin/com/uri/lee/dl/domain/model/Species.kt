package com.uri.lee.dl.domain.model

/**
 * A species from the published catalog.
 *
 * [id] is the GBIF species key. The herb model outputs the same key as its label, so a
 * recognition result maps straight to a catalog entry.
 */
data class Species(
    val id: Long,
    /** Canonical scientific name without authorship, e.g. "Polyscias fruticosa". */
    val scientificName: String,
    /** Naming authority, e.g. "(L.) Harms"; may be blank. */
    val authorship: String,
    val family: String,
    val genus: String,
    /** Vietnamese names, preferred name first; may be empty. */
    val vietnameseNames: List<String>,
    /** English common names, preferred name first; may be empty. */
    val englishNames: List<String>,
    /** The rest of GBIF's record, shown in the species' full details; blank where unknown. */
    val taxonomy: Taxonomy = Taxonomy(),
) {
    val preferredVietnameseName: String? get() = vietnameseNames.firstOrNull()
    val preferredEnglishName: String? get() = englishNames.firstOrNull()

    /**
     * The name shown first: Vietnamese for readers in or from Vietnam, English for everyone else,
     * and the scientific name when the species has no common name in that language.
     */
    fun displayName(vietnameseFirst: Boolean): String =
        (if (vietnameseFirst) preferredVietnameseName else preferredEnglishName) ?: scientificName

    /** The common name in the other language, shown under [displayName]; null when there's none. */
    fun otherName(vietnameseFirst: Boolean): String? =
        if (vietnameseFirst) preferredEnglishName else preferredVietnameseName
}

/** GBIF's classification above the family, and the name's nomenclature. */
data class Taxonomy(
    val kingdom: String = "",
    val phylum: String = "",
    val className: String = "",
    val order: String = "",
    /** GBIF's rank, such as "SPECIES" or "VARIETY". */
    val rank: String = "",
    /** GBIF's taxonomic status, such as "ACCEPTED" or "DOUBTFUL". */
    val status: String = "",
    /** Where the name was first published. */
    val publishedIn: String = "",
    /** The name it was first described under, when it has since moved. */
    val basionym: String = "",
)
