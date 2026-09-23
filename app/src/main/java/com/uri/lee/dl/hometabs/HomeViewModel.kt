package com.uri.lee.dl.hometabs

import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.model.HerbPageKey
import com.uri.lee.dl.domain.model.HerbSummary
import com.uri.lee.dl.domain.repository.HerbRepository
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
    /** Reload the first page of all herbs. */
    data object Refresh : HomeAction

    /** The list was scrolled to its end. */
    data object LoadMore : HomeAction
}

data class HomeState(
    val allHerbs: List<HerbSummary> = emptyList(),
    val favorites: List<HerbSummary> = emptyList(),
    val history: List<HerbSummary> = emptyList(),
    val isLoadingPage: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Throwable)
}

/** The three home tabs: all herbs (paged), favourites and history. */
class HomeViewModel(
    private val herbs: HerbRepository,
    library: UserLibraryRepository,
    private val sortByVietnameseName: Boolean,
) : MviViewModel<HomeState, HomeAction>(HomeState()) {

    private var nextPage: HerbPageKey? = null
    private var hasMore = true
    private var loading: Job? = null

    init {
        summaries(library.observeFavorites()).onEach { setState { copy(favorites = it) } }.launchIn(viewModelScope)
        summaries(library.observeHistory()).onEach { setState { copy(history = it) } }.launchIn(viewModelScope)
        loadPage(reset = true)
    }

    override fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> loadPage(reset = true)
            HomeAction.LoadMore -> if (hasMore && loading?.isActive != true) loadPage(reset = false)
        }
    }

    private fun loadPage(reset: Boolean) {
        loading?.cancel()
        loading = viewModelScope.launch {
            setState { copy(isLoadingPage = true) }
            try {
                val page = herbs.page(after = if (reset) null else nextPage, size = PAGE_SIZE, sortByVietnameseName)
                nextPage = page.next
                hasMore = page.next != null
                setState {
                    val loaded = if (reset) page.herbs else allHerbs + page.herbs
                    copy(allHerbs = loaded.distinctBy { it.id }, isLoadingPage = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e)
                setState { copy(isLoadingPage = false) }
            }
        }
    }

    private fun summaries(ids: Flow<List<Long>>): Flow<List<HerbSummary>> =
        ids.map { herbs.summaries(it) }.catch { reportError(it) }

    private fun reportError(e: Throwable) {
        Timber.e(e)
        setState { copy(error = HomeState.Error(e)) }
    }

    companion object {
        const val PAGE_SIZE = 10
    }
}
