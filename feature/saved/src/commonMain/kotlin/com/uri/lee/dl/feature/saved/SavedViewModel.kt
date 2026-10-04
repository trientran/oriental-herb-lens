package com.uri.lee.dl.feature.saved

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

enum class SavedTab { FAVORITES, HISTORY }

sealed interface SavedAction {
    data class SelectTab(val tab: SavedTab) : SavedAction
}

data class SavedState(
    val tab: SavedTab = SavedTab.FAVORITES,
    /** Null until loaded. */
    val favorites: List<Species>? = null,
    val history: List<Species>? = null,
    val hasError: Boolean = false,
) {
    val shown: List<Species>? get() = if (tab == SavedTab.FAVORITES) favorites else history
}

/** Favourites and recently viewed herbs, kept on the device. */
class SavedViewModel(
    private val catalog: SpeciesRepository,
    library: UserLibraryRepository,
) : MviViewModel<SavedState, SavedAction>(SavedState()) {

    init {
        resolved(library.observeFavorites()).onEach { setState { copy(favorites = it) } }.launchIn(viewModelScope)
        resolved(library.observeHistory()).onEach { setState { copy(history = it) } }.launchIn(viewModelScope)
    }

    override fun onAction(action: SavedAction) {
        when (action) {
            is SavedAction.SelectTab -> setState { copy(tab = action.tab) }
        }
    }

    /** Ids in library order, looked up in the catalog; ids the catalog no longer has are dropped. */
    private fun resolved(ids: Flow<List<Long>>): Flow<List<Species>> =
        ids.map { list -> catalog.getAll(list).let { found -> list.mapNotNull(found::get) } }
            .catch {
                log.e(it) { "Saved list unavailable" }
                setState { copy(hasError = true) }
            }

    private companion object {
        val log = Logger.withTag("Saved")
    }
}
