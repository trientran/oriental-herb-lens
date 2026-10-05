package com.uri.lee.dl.feature.herbdetails

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.SpeciesPhoto
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
}

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
) {
    /** User contributions first, then GBIF photos. */
    val photos: List<SpeciesPhoto> get() = userPhotos + referencePhotos
}

/** A species: its names and classification from the catalog, user and GBIF photos, favourite state. */
class HerbDetailsViewModel(
    herbId: Long,
    private val catalog: SpeciesRepository,
    private val userPhotos: PhotoRepository,
    private val referencePhotos: ReferencePhotoRepository,
    private val library: UserLibraryRepository,
) : MviViewModel<HerbDetailsState, HerbDetailsAction>(HerbDetailsState(herbId)) {

    init {
        load()
        userPhotos.observeUserPhotos(herbId)
            .onEach { setState { copy(userPhotos = it) } }
            .catch { log.w(it) { "User photos unavailable" } } // e.g. offline: GBIF photos may still show
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
        }
    }

    private fun load() = viewModelScope.launch {
        val id = currentState.herbId
        try {
            val species = catalog.get(id)
            setState { copy(species = species, notFound = species == null, photosLoading = species != null) }
            if (species == null) return@launch
            library.recordViewed(id)
            loadReferencePhotos(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Species $id unavailable" }
            setState { copy(hasError = true, photosLoading = false) }
        }
    }

    private fun toggleFavorite() {
        val favorite = !currentState.isFavorite
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
