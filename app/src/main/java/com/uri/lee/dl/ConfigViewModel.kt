package com.uri.lee.dl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException

class ConfigViewModel : ViewModel() {

    private val stateFlow = MutableStateFlow(ConfigState())

    private lateinit var listenerRegistration: ListenerRegistration

    /** Emits the current state. */
    fun state(): Flow<ConfigState> = stateFlow

    /** Retrieves the current state. */
    val state: ConfigState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        try {
            liveMobileUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(error = ConfigState.Error(e)) }
        }
    }

    private fun liveMobileUpdate() {
        listenerRegistration = configCollection.document("mobile").addSnapshotListener { snapshot, e ->
            viewModelScope.launch {
                if (e != null) {
                    Timber.e(e)
                    setState { copy(error = ConfigState.Error(e)) }
                    return@launch
                }
                if (snapshot != null && snapshot.exists()) {
                    val mobile = snapshot.toObject<FireStoreMobile>()
                    viewModelScope.launch { setState { copy(mobile = mobile) } }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (this::listenerRegistration.isInitialized) listenerRegistration.remove()
    }

    private inline fun setState(copiedState: ConfigState.() -> ConfigState) = stateFlow.update(copiedState)

}

data class ConfigState(
    val mobile: FireStoreMobile? = null,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
