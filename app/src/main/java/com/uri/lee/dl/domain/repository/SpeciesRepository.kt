package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.Species

/** Read access to the species catalog that ships with the app and is updated through content sync. */
interface SpeciesRepository {
    suspend fun get(id: Long): Species?

    /** Species for the given ids; ids not in the catalog are absent from the result. */
    suspend fun getAll(ids: Collection<Long>): Map<Long, Species>

    suspend fun all(): List<Species>
}
