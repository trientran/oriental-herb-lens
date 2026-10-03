package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.SpeciesPhoto

/** Third-party photos of a species (GBIF), each carrying the credit that must be shown with it. */
interface ReferencePhotoRepository {
    suspend fun photos(speciesId: Long, limit: Int = 30): List<SpeciesPhoto>
}
