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
) {
    val preferredVietnameseName: String? get() = vietnameseNames.firstOrNull()
    val preferredEnglishName: String? get() = englishNames.firstOrNull()
}
