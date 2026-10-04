package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.usecase.NotSignedInException
import kotlinx.coroutines.tasks.await

/**
 * The app's only two Firestore writes:
 * - photo URLs, appended to the `images` map of `herbs/{speciesKey}` (rules forbid removing or
 *   changing existing entries)
 * - Vietnamese name suggestions, created in `nameSuggestions` for the admin to review; the app
 *   itself shows only the catalog's names
 */
class FirestoreContributionRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
) : ContributionRepository {

    override suspend fun addImages(herbId: Long, images: List<UploadedImage>) {
        val entries = images.associate { it.url to HerbDocumentMapper.imageDetail(it.uploaderId, it.location) }
        db.collection(FirestorePaths.HERBS).document(herbId.toString())
            .set(mapOf(FirestorePaths.HERB_IMAGES to entries), SetOptions.merge())
            .await()
    }

    override suspend fun suggestVietnameseName(herbId: Long, name: String) {
        val uid = auth.currentUserId ?: throw NotSignedInException()
        db.collection(FirestorePaths.NAME_SUGGESTIONS).add(
            mapOf(
                "speciesKey" to herbId,
                "viName" to name.trim(),
                "uid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }
}
