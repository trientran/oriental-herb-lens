package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.ContributionRecord
import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.model.Contribution
import com.uri.lee.dl.domain.model.ContributionHistoryRepository
import com.uri.lee.dl.domain.model.ContributionKind
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The signed-in user's own record of what they shared (`users/{uid}/contributions`, readable by
 * them alone), and the checks that it's still shown. The records are written by the server
 * (firebase/functions) when a photo, name or model is shared, from any app version.
 */
internal class FirestoreContributionHistory(
    private val firestore: FirestoreClient,
    private val auth: AuthRepository,
) : ContributionHistoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<Contribution>> = auth.observeUserId().flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            firestore.observeContributions(uid)
                .map { records -> records.mapNotNull { it.toContribution() } }
                .catch { e ->
                    log.w(e) { "Contributions unavailable" }
                    emit(emptyList())
                }
        }
    }

    override suspend fun stillShown(contributions: List<Contribution>): Set<String> {
        val shown = mutableSetOf<String>()
        contributions.filter { it.kind == ContributionKind.NAME }.mapTo(shown) { it.id }
        contributions.filter { it.kind == ContributionKind.PHOTO && it.speciesKey != null }.groupBy { it.speciesKey!! }
            .forEach { (species, photos) ->
                // Unknown (offline): counted as shown rather than taking a badge away
                val listed = readOrNull { firestore.herbImageUrls(species) }
                photos.filter { listed == null || it.url in listed }.mapTo(shown) { it.id }
            }
        contributions.filter { it.kind == ContributionKind.MODEL && it.modelId != null }.forEach { model ->
            val listed = readOrNull { firestore.sharedModel(model.modelId!!) != null }
            if (listed != false) shown += model.id
        }
        return shown
    }

    override suspend fun modelAdds(modelIds: List<String>): Int =
        modelIds.distinct().sumOf { id -> readOrNull { firestore.sharedModel(id)?.adds } ?: 0 }

    private suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w(e) { "Couldn't check a contribution" }
        null
    }

    private fun ContributionRecord.toContribution(): Contribution? = Contribution(
        id = id,
        kind = when (kind) {
            "photo" -> ContributionKind.PHOTO
            "name" -> ContributionKind.NAME
            "model" -> ContributionKind.MODEL
            else -> return null
        },
        speciesKey = speciesKey,
        url = url,
        modelId = modelId,
        neededPhotos = neededPhotos,
        createdAtMillis = createdAtMillis,
    )

    private companion object {
        val log = Logger.withTag("Contributions")
    }
}
