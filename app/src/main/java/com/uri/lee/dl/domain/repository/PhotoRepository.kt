package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.SpeciesPhoto
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    /** Photos users contributed for [speciesId], newest changes included. */
    fun observeUserPhotos(speciesId: Long): Flow<List<SpeciesPhoto>>
}
