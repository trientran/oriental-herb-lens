package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.search.SpeciesMatch

/** The species catalog: bundled with the app, updated through content sync, stored on the device. */
interface SpeciesRepository {
    suspend fun get(id: Long): Species?

    /** Species for the given ids; ids not in the catalog are absent from the result. */
    suspend fun getAll(ids: Collection<Long>): Map<Long, Species>

    suspend fun all(): List<Species>

    /** A page of all species, sorted by preferred Vietnamese name (unnamed last) or by scientific name. */
    suspend fun page(offset: Int, limit: Int, sortByVietnameseName: Boolean): List<Species>

    /** Diacritic-insensitive search over every name; best matches first. */
    suspend fun search(query: String, limit: Int = 50): List<SpeciesMatch>
}
