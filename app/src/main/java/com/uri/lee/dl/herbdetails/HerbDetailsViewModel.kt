package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface HerbDetailsAction {
    data object ToggleFavorite : HerbDetailsAction
}

data class HerbDetailsState(
    val herbId: Long,
    /** Null until loaded, or when the catalog has no such species ([notFound]). */
    val species: Species? = null,
    val notFound: Boolean = false,
    val photos: List<SpeciesPhoto> = emptyList(),
    val isFavorite: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Throwable)
}

/** Backs the species details screen and its tabs, which share it through the activity. */
class HerbDetailsViewModel(
    savedState: SavedStateHandle,
    private val catalog: SpeciesRepository,
    photos: PhotoRepository,
    private val library: UserLibraryRepository,
) : MviViewModel<HerbDetailsState, HerbDetailsAction>(
    HerbDetailsState(herbId = requireNotNull(savedState.get<Long>(HERB_ID)) { "HerbDetailsActivity needs $HERB_ID" })
) {

    init {
        val id = currentState.herbId
        viewModelScope.launch {
            try {
                val species = catalog.get(id)
                setState { copy(species = species, notFound = species == null) }
                if (species != null) library.recordViewed(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e)
            }
        }
        photos.observeUserPhotos(id)
            .onEach { setState { copy(photos = it) } }
            .catch { Timber.w(it, "User photos unavailable") }
            .launchIn(viewModelScope)
        library.observeFavorites()
            .onEach { favorites -> setState { copy(isFavorite = id in favorites) } }
            .catch { reportError(it) }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: HerbDetailsAction) {
        when (action) {
            HerbDetailsAction.ToggleFavorite -> {
                val favorite = !currentState.isFavorite
                setState { copy(isFavorite = favorite) } // shown immediately; the library confirms it
                viewModelScope.launch {
                    try {
                        library.setFavorite(currentState.herbId, favorite)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        setState { copy(isFavorite = !favorite) }
                        reportError(e)
                    }
                }
            }
        }
    }

    private fun reportError(e: Throwable) {
        Timber.e(e)
        setState { copy(error = HerbDetailsState.Error(e)) }
    }
}
