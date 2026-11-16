package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.upload.NotSignedInException
import kotlinx.coroutines.flow.first

/**
 * The app's only two Firestore writes:
 * - photo URLs, appended to the `images` map of `herbs/{speciesKey}` (rules forbid removing or
 *   changing existing entries)
 * - common (vernacular) name suggestions, with their language, created in `nameSuggestions` for
 *   the admin to review; the app itself shows only the catalog's names
 *
 * The server notes each on the contributor's own record (firebase/functions). Photos carry the
 * name the contributor chose to be credited by, if any (Profile).
 */
internal class FirestoreContributionRepository(
    private val firestore: FirestoreClient,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
) : ContributionRepository {

    override suspend fun addImages(herbId: Long, images: List<UploadedImage>) {
        val credit = settings.creditName.first()
        val entries = images.associate { it.url to HerbDocumentMapper.imageDetail(it.uploaderId, it.location, credit) }
        firestore.addHerbImages(herbId, entries)
    }

    override suspend fun suggestName(herbId: Long, language: String, name: String) {
        val uid = auth.currentUserId ?: throw NotSignedInException()
        firestore.addNameSuggestion(herbId, language, name.trim(), uid)
    }
}
