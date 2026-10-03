package com.uri.lee.dl.hometabs

import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface HomeAction {
    /** The all-species list was scrolled to its end. */
    data object LoadMore : HomeAction
}

data class HomeState(
    val allSpecies: List<Species> = emptyList(),
    val favorites: List<Species> = emptyList(),
    val history: List<Species> = emptyList(),
    val error: Error? = null,
) {
    data class Error(val exception: Throwable)
}

/** The three home tabs: every species in the catalog (paged), favourites and history. */
class HomeViewModel(
    private val species: SpeciesRepository,
    library: UserLibraryRepository,
    private val sortByVietnameseName: Boolean,
) : MviViewModel<HomeState, HomeAction>(HomeState()) {

    private var hasMore = true
    private var loading: Job? = null

    init {
        named(library.observeFavorites()).onEach { setState { copy(favorites = it) } }.launchIn(viewModelScope)
        named(library.observeHistory()).onEach { setState { copy(history = it) } }.launchIn(viewModelScope)
        loadMore()
    }

    override fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.LoadMore -> loadMore()
        }
    }

    private fun loadMore() {
        if (!hasMore || loading?.isActive == true) return
        loading = viewModelScope.launch {
            try {
                val offset = currentState.allSpecies.size
                val page = species.page(offset, PAGE_SIZE, sortByVietnameseName)
                hasMore = page.size == PAGE_SIZE
                setState { copy(allSpecies = allSpecies + page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e)
            }
        }
    }

    /** Ids in library order, resolved against the catalog; ids the catalog no longer has are dropped. */
    private fun named(ids: Flow<List<Long>>): Flow<List<Species>> =
        ids.map { list -> species.getAll(list).let { found -> list.mapNotNull(found::get) } }.catch { reportError(it) }

    private fun reportError(e: Throwable) {
        Timber.e(e)
        setState { copy(error = HomeState.Error(e)) }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
