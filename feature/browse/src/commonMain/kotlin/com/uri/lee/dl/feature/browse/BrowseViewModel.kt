package com.uri.lee.dl.feature.browse

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.search.SpeciesMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface BrowseAction {
    data class QueryChanged(val query: String) : BrowseAction

    /** The catalog list was scrolled near its end. */
    data object LoadMore : BrowseAction
    data object Retry : BrowseAction
}

data class BrowseState(
    /** The whole catalog, a page at a time, in name order. */
    val species: List<Species> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    /** Search results for [query]; meaningful only while [isSearching]. */
    val results: List<SpeciesMatch> = emptyList(),
    val hasError: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

/** The catalog, browsable a page at a time and searchable as the user types. Works offline. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowseViewModel(
    private val catalog: SpeciesRepository,
    private val sortByVietnameseName: Boolean,
) : MviViewModel<BrowseState, BrowseAction>(BrowseState()) {

    private val queries = MutableStateFlow("")
    private var hasMore = true
    private var loading: Job? = null

    init {
        queries
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .mapLatest { query ->
                query to try {
                    if (query.isBlank()) emptyList() else catalog.search(query, SEARCH_LIMIT)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.e(e) { "Search failed" }
                    emptyList()
                }
            }
            .onEach { (query, results) -> setState { if (this.query == query) copy(results = results) else this } }
            .launchIn(viewModelScope)
        loadMore()
    }

    override fun onAction(action: BrowseAction) {
        when (action) {
            is BrowseAction.QueryChanged -> {
                setState { copy(query = action.query) }
                queries.value = action.query
            }
            BrowseAction.LoadMore -> loadMore()
            BrowseAction.Retry -> {
                setState { copy(hasError = false) }
                loadMore()
            }
        }
    }

    private fun loadMore() {
        if (!hasMore || loading?.isActive == true || currentState.hasError) return
        loading = viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                val page = catalog.page(currentState.species.size, PAGE_SIZE, sortByVietnameseName)
                hasMore = page.size == PAGE_SIZE
                setState { copy(species = species + page, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Catalog page failed" }
                setState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 40
        const val SEARCH_DEBOUNCE_MS = 150L
        const val SEARCH_LIMIT = 100
        private val log = Logger.withTag("Browse")
    }
}
