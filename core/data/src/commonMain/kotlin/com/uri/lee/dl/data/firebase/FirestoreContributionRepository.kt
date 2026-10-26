package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.Contribution
import com.uri.lee.dl.domain.model.ContributionHistoryRepository
import com.uri.lee.dl.domain.model.ContributionKind
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.upload.NotSignedInException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The app's only two Firestore writes:
 * - photo URLs, appended to the `images` map of `herbs/{speciesKey}` (rules forbid removing or
 *   changing existing entries)
 * - common (vernacular) name suggestions, with their language, created in `nameSuggestions` for
 *   the admin to review; the app itself shows only the catalog's names
 *
 * Each is also noted on the contributor's own record ([history]), with photos marked when the
 * herb model didn't know the species yet. Photos carry the name the contributor chose to be
 * credited by, if any (Profile).
 */
internal class FirestoreContributionRepository(
    private val firestore: FirestoreClient,
    private val auth: AuthRepository,
    private val history: ContributionHistoryRepository,
    private val settings: SettingsRepository,
    private val classifier: HerbClassifier,
) : ContributionRepository {

    override suspend fun addImages(herbId: Long, images: List<UploadedImage>) {
        val credit = settings.creditName.first()
        val entries = images.associate { it.url to HerbDocumentMapper.imageDetail(it.uploaderId, it.location, credit) }
        firestore.addHerbImages(herbId, entries)
        // Unknown when the model's labels can't be read: then no photo is marked as filling a gap
        val known = knownSpecies()
        val needed = known.isNotEmpty() && herbId !in known
        images.filter { it.uploaderId == auth.currentUserId }.forEach {
            history.record(Contribution("", ContributionKind.PHOTO, speciesKey = herbId, url = it.url, neededPhotos = needed))
        }
    }

    override suspend fun suggestName(herbId: Long, language: String, name: String) {
        val uid = auth.currentUserId ?: throw NotSignedInException()
        firestore.addNameSuggestion(herbId, language, name.trim(), uid)
        history.record(Contribution("", ContributionKind.NAME, speciesKey = herbId))
    }

    private suspend fun knownSpecies(): Set<Long> = try {
        classifier.knownSpecies()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptySet()
    }
}
