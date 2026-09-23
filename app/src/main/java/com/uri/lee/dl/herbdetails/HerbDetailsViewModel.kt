package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.domain.model.HerbProfile
import com.uri.lee.dl.domain.repository.HerbRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface HerbDetailsAction {
    data object ToggleFavorite : HerbDetailsAction
}

data class HerbDetailsState(
    val herbId: Long,
    val profile: HerbProfile? = null,
    val isFavorite: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Throwable)
}

/** Backs the herb details screen and its tabs, which share it through the activity. */
class HerbDetailsViewModel(
    savedState: SavedStateHandle,
    herbs: HerbRepository,
    private val library: UserLibraryRepository,
) : MviViewModel<HerbDetailsState, HerbDetailsAction>(
    HerbDetailsState(herbId = requireNotNull(savedState.get<Long>(HERB_ID)) { "HerbDetailsActivity needs $HERB_ID" })
) {

    init {
        val id = currentState.herbId
        herbs.observeProfile(id)
            .onEach { profile -> setState { copy(profile = profile) } }
            .catch { reportError(it) }
            .launchIn(viewModelScope)
        library.observeFavorites()
            .onEach { favorites -> setState { copy(isFavorite = id in favorites) } }
            .catch { reportError(it) }
            .launchIn(viewModelScope)
        // Record the view once the herb is known to exist.
        viewModelScope.launch {
            try {
                state.map { it.profile }.filterNotNull().first()
                library.recordViewed(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Couldn't record herb $id in history")
            }
        }
    }

    override fun onAction(action: HerbDetailsAction) {
        when (action) {
            HerbDetailsAction.ToggleFavorite -> {
                val favorite = !currentState.isFavorite
                setState { copy(isFavorite = favorite) } // shown immediately; the listener confirms it
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
