package com.uri.lee.dl.feature.herbdetails

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.moderation.HiddenContent
import com.uri.lee.dl.domain.moderation.ModerationRepository
import com.uri.lee.dl.domain.moderation.ReportReason
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface HerbDetailsAction {
    data object ToggleFavorite : HerbDetailsAction

    /** Opens the full-screen viewer at [index] of [HerbDetailsState.photos]; null closes it. */
    data class ViewPhoto(val index: Int?) : HerbDetailsAction
    data object Retry : HerbDetailsAction

    /** Reports a shared photo to the administrator; it's hidden for this user at once. */
    data class Report(val photo: SpeciesPhoto, val reason: ReportReason) : HerbDetailsAction

    /** Hides every photo this contributor shared, for this user. */
    data class HideContributor(val uploaderId: String) : HerbDetailsAction

    /** The [HerbDetailsState.notice] was shown. */
    data object NoticeShown : HerbDetailsAction
}

/** A short confirmation after a moderation action. */
enum class ModerationNotice { REPORTED, CONTRIBUTOR_HIDDEN, FAILED }

data class HerbDetailsState(
    val herbId: Long,
    /** Null until loaded, or when the catalog has no such species ([notFound]). */
    val species: Species? = null,
    val notFound: Boolean = false,
    val userPhotos: List<SpeciesPhoto> = emptyList(),
    /** GBIF photos; empty while loading or offline. */
    val referencePhotos: List<SpeciesPhoto> = emptyList(),
    /** GBIF photos are still being fetched, so no photos doesn't mean none exist yet. */
    val photosLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val viewingPhoto: Int? = null,
    val hasError: Boolean = false,
    /** Photos and contributors this user hid or reported. */
    val hidden: HiddenContent = HiddenContent(),
    val notice: ModerationNotice? = null,
) {
    /** User contributions first, then GBIF photos, without the ones this user hid. */
    val photos: List<SpeciesPhoto> get() = (userPhotos + referencePhotos).filterNot { hidden.hides(it.url, it.uploaderId) }
}

/** A species: its names and classification from the catalog, user and GBIF photos, favourite state. */
class HerbDetailsViewModel(
    herbId: Long,
    private val catalog: SpeciesRepository,
    private val userPhotos: PhotoRepository,
    private val referencePhotos: ReferencePhotoRepository,
    private val library: UserLibraryRepository,
    private val moderation: ModerationRepository,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<HerbDetailsState, HerbDetailsAction>(HerbDetailsState(herbId)) {

    init {
        load()
        userPhotos.observeUserPhotos(herbId)
            .onEach { setState { copy(userPhotos = it) } }
            .catch { log.w(it) { "User photos unavailable" } } // e.g. offline: GBIF photos may still show
            .launchIn(viewModelScope)
        moderation.observeHidden()
            .onEach { setState { copy(hidden = it) } }
            .catch { log.w(it) { "Hidden photos unavailable" } }
            .launchIn(viewModelScope)
        library.observeFavorites()
            .onEach { favorites -> setState { copy(isFavorite = herbId in favorites) } }
            .catch { log.e(it) { "Favourites unavailable" } }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: HerbDetailsAction) {
        when (action) {
            HerbDetailsAction.ToggleFavorite -> toggleFavorite()
            is HerbDetailsAction.ViewPhoto -> setState { copy(viewingPhoto = action.index) }
            HerbDetailsAction.Retry -> {
                setState { copy(hasError = false) }
                load()
            }
            is HerbDetailsAction.Report -> moderate(ModerationNotice.REPORTED) {
                analytics.log(AnalyticsEvent.PhotoReported(action.reason.name))
                moderation.report(currentState.herbId, action.photo.url, action.photo.uploaderId, action.reason)
            }
            is HerbDetailsAction.HideContributor -> moderate(ModerationNotice.CONTRIBUTOR_HIDDEN) {
                moderation.hideContributor(action.uploaderId)
            }
            HerbDetailsAction.NoticeShown -> setState { copy(notice = null) }
        }
    }

    private fun load() = viewModelScope.launch {
        val id = currentState.herbId
        try {
            val species = catalog.get(id)
            setState { copy(species = species, notFound = species == null, photosLoading = species != null) }
            if (species == null) return@launch
            analytics.log(AnalyticsEvent.SpeciesViewed(id))
            library.recordViewed(id)
            loadReferencePhotos(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Species $id unavailable" }
            setState { copy(hasError = true, photosLoading = false) }
        }
    }

    /** Closes the viewer (the photo is gone from the list) and confirms what happened. */
    private fun moderate(done: ModerationNotice, block: suspend () -> Unit) {
        setState { copy(viewingPhoto = null) }
        viewModelScope.launch {
            val notice = try {
                block()
                done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Moderation action failed" }
                ModerationNotice.FAILED
            }
            setState { copy(notice = notice) }
        }
    }

    private fun toggleFavorite() {
        val favorite = !currentState.isFavorite
        if (favorite) analytics.log(AnalyticsEvent.FavoriteAdded(currentState.herbId))
        setState { copy(isFavorite = favorite) } // shown immediately; the library confirms it
        viewModelScope.launch {
            try {
                library.setFavorite(currentState.herbId, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Favourite not saved" }
                setState { copy(isFavorite = !favorite) }
            }
        }
    }

    private suspend fun loadReferencePhotos(id: Long) {
        try {
            val gbif = referencePhotos.photos(id)
            setState { copy(referencePhotos = gbif) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "GBIF photos unavailable" } // offline is normal; user photos still show
        } finally {
            setState { copy(photosLoading = false) }
        }
    }

    private companion object {
        val log = Logger.withTag("HerbDetails")
    }
}
