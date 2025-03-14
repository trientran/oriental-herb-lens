package com.uri.lee.dl.hometabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeTabViewModel() : ViewModel() {

    private val stateFlow = MutableStateFlow(PageState())


    /** Emits the current state. */
    fun state(): Flow<PageState> = stateFlow

    /** Retrieves the current state. */
    val state: PageState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun setIndex(index: Int) {
        Timber.d("setIndex")
        viewModelScope.launch {
            setState { copy(tabIndex = index) }
        }
    }

    private inline fun setState(copiedState: PageState.() -> PageState) = stateFlow.update(copiedState)

}

data class PageState(val tabIndex: Int? = null)
