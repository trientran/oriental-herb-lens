package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User photos live in the `images` map of `herbs/{speciesKey}`: one document read per species. */
class FirestorePhotoRepository(private val db: FirebaseFirestore) : PhotoRepository {

    override fun observeUserPhotos(speciesId: Long): Flow<List<SpeciesPhoto>> =
        db.collection(FirestorePaths.HERBS).document(speciesId.toString()).snapshots().map { doc ->
            (doc.get(FirestorePaths.HERB_IMAGES) as? Map<*, *>).orEmpty().mapNotNull { (url, detail) ->
                if (url is String && detail is String) HerbDocumentMapper.toPhoto(url, detail) else null
            }
        }
}
