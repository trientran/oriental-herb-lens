package com.uri.lee.dl.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One-way data flow: the UI observes a single [state] and reports user input only through
 * [onAction]. The ViewModel alone changes state, through [setState].
 */
abstract class MviViewModel<S, A>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    protected val currentState: S get() = _state.value

    abstract fun onAction(action: A)

    protected fun setState(reduce: S.() -> S) = _state.update(reduce)
}
