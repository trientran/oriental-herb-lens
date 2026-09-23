package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import kotlinx.coroutines.tasks.await

/**
 * Writes contributions straight onto the herb document, as the app always has. Phase 2 moves both
 * to create-only collections (decision D3) together with the new security rules.
 */
class FirestoreContributionRepository(private val db: FirebaseFirestore) : ContributionRepository {

    private fun herb(id: Long) = db.collection(FirestorePaths.HERBS).document(id.toString())

    override suspend fun addImages(herbId: Long, images: List<UploadedImage>) {
        val entries = images.associate { it.url to HerbDocumentMapper.imageDetail(it.uploaderId, it.location) }
        herb(herbId).set(mapOf(FirestorePaths.HERB_IMAGES to entries), SetOptions.merge()).await()
    }

    override suspend fun suggestVietnameseName(herbId: Long, name: String) {
        herb(herbId).update(FirestorePaths.HERB_VI_NAME, name.trim()).await()
    }
}
