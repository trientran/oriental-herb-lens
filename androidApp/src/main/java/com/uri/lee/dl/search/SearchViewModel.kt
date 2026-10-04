package com.uri.lee.dl.search

import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.search.SpeciesMatch
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
}

data class SearchState(
    val query: String = "",
    val results: List<SpeciesMatch> = emptyList(),
)

/** Searches the local catalog as the user types; works offline. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val species: SpeciesRepository) : MviViewModel<SearchState, SearchAction>(SearchState()) {

    private val queries = MutableStateFlow("")

    init {
        queries
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .mapLatest { query ->
                try {
                    query to species.search(query, LIMIT)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Search failed")
                    query to emptyList()
                }
            }
            .onEach { (query, results) -> setState { if (this.query == query) copy(results = results) else this } }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> {
                setState { copy(query = action.query) }
                queries.value = action.query
            }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 150L
        const val LIMIT = 100
    }
}
