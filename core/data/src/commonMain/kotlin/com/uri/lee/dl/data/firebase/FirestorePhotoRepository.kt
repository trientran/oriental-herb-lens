package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User photos live in the `images` map of `herbs/{speciesKey}`: one document read per species. */
internal class FirestorePhotoRepository(private val firestore: FirestoreClient) : PhotoRepository {

    override fun observeUserPhotos(speciesId: Long): Flow<List<SpeciesPhoto>> =
        firestore.observeHerbImages(speciesId).map { images ->
            images.map { (url, detail) -> HerbDocumentMapper.toPhoto(url, detail) }
        }
}
