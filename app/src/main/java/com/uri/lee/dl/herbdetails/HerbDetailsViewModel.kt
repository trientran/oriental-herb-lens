package com.uri.lee.dl.herbdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.uri.lee.dl.herbCollection
import com.uri.lee.dl.hometabs.toHerb
import com.uri.lee.dl.instantsearch.Herb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class HerbDetailsViewModel : ViewModel() {

    private lateinit var realtimeData: ListenerRegistration

    private val stateFlow = MutableStateFlow(HerbDetailsState())

    fun state(): Flow<HerbDetailsState> = stateFlow

    val state: HerbDetailsState get() = stateFlow.value

    fun setId(objectID: String) {
        Timber.d("setId")
        viewModelScope.launch { setState { copy(herb = Herb(objectID = objectID)) } }
    }

    fun setHerb(herb: Herb) {
        Timber.d("setHerb")
        viewModelScope.launch { setState { copy(herb = herb) } }
    }

    fun setLike() {
        viewModelScope.launch { setState { copy(isLiked = !state.isLiked) } }
    }

    fun getOnLineHerbData() {
        herbCollection.takeIf { state.herb?.objectID != null }?.document(state.herb!!.objectID)?.apply {
            realtimeData = addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Timber.e(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val herb = snapshot.toHerb()
                    viewModelScope.launch { setState { copy(herb = herb) } }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeData.remove()
    }

    private inline fun setState(copiedState: HerbDetailsState.() -> HerbDetailsState) = stateFlow.update(copiedState)
}

data class HerbDetailsState(
    val herb: Herb? = null,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val event: Event? = null,
) {
    sealed interface Event {
        data class Error(val exception: Exception) : Event
    }
}
