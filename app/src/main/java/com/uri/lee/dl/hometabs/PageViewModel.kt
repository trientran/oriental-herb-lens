package com.uri.lee.dl.hometabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.uri.lee.dl.*
import com.uri.lee.dl.instantsearch.Herb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException
import kotlin.random.Random

val TAB_TITLES = arrayOf(R.string.tab_favorite, R.string.tab_history, R.string.tab_random)

class PageViewModel(application: Application) : AndroidViewModel(application) {

    private val stateFlow = MutableStateFlow(PageState())
    private val application = getApplication<BaseApplication>()

    private lateinit var personalListListenerRegistration: ListenerRegistration

    private var personalHerbIdList: List<*>? = null

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
            setState { copy(index = index) }
            try {
                if (authUI.auth.currentUser?.email == null) return@launch
                when (TAB_TITLES[index]) {
                    R.string.tab_favorite -> liveHistoryOrFavoriteUpdate(DocType.FAVORITE)
                    R.string.tab_history -> liveHistoryOrFavoriteUpdate(DocType.HISTORY)
                    R.string.tab_random -> processRandom()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(event = PageState.Event.Error(e)) }
            }
        }
    }

    fun processRandom(numberOfDocumentsToLoad: Int = 10) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            repeat(numberOfDocumentsToLoad) { index ->
                val randomId = Random.nextInt(from = 1000, until = 4939).toString()
                herbCollection.document(randomId).get().await().let {
                    val currentHerbList = state.herbs.toMutableList()
                    currentHerbList.add(it.toHerb())
                    setState { copy(herbs = currentHerbList) }
                    if (index == numberOfDocumentsToLoad - 1) setState { copy(isLoading = false) }
                }
            }
            setState { copy(isLoading = false) }
        }
    }

    private fun liveHistoryOrFavoriteUpdate(docType: DocType) {
        personalListListenerRegistration = userCollection.document(authUI.auth.uid!!)
            .addSnapshotListener { snapshot, e ->
                viewModelScope.launch {
                    setState { copy(isLoading = true) }
                    if (e != null) {
                        Timber.e(e)
                        setState { copy(event = PageState.Event.Error(e)) }
                        setState { copy(isLoading = false) }
                        return@launch
                    }
                    if (snapshot != null && snapshot.exists()) {
                        personalHerbIdList = (snapshot.get(docType.docType) as? List<*>)?.reversed()
                        if (!personalHerbIdList.isNullOrEmpty()) {
                            val newHerbList = mutableListOf<Herb>()
                            repeat(10) {
                                if (it == personalHerbIdList!!.size) {
                                    setState { copy(isLoading = false) }
                                    return@launch
                                }
                                val currentHerbSnapshot =
                                    herbCollection.document(personalHerbIdList!![it] as String).get().await()
                                newHerbList.add(currentHerbSnapshot.toHerb())
                                setState { copy(herbs = newHerbList.toList()) }
                            }
                        }
                    }
                    setState { copy(isLoading = false) }
                }
            }
    }

    fun loadMoreHistoryOrFavorite(numberOfRecyclerViewItemsLoaded: Int, numberOfDocumentsToLoad: Int = 10) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            if (!personalHerbIdList.isNullOrEmpty()) {
                var numberOfNewDocsLoaded = 0
                var currentItemIndex = numberOfRecyclerViewItemsLoaded
                while (currentItemIndex < personalHerbIdList!!.size) {
                    if (numberOfNewDocsLoaded >= numberOfDocumentsToLoad) {
                        setState { copy(isLoading = false) }
                        return@launch
                    }
                    val currentHerbId = personalHerbIdList!![currentItemIndex] as String
                    val currentHerbSnapshot = herbCollection.document(currentHerbId).get().await()
                    val currentHerbList = state.herbs.toMutableList()
                    currentHerbList.add(currentHerbSnapshot.toHerb())
                    setState { copy(herbs = currentHerbList) }
                    currentItemIndex++
                    numberOfNewDocsLoaded++
                }
            }
            setState { copy(isLoading = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (this::personalListListenerRegistration.isInitialized) personalListListenerRegistration.remove()
    }

    private inline fun setState(copiedState: PageState.() -> PageState) = stateFlow.update(copiedState)

}

data class PageState(
    val index: Int? = null,
    val herbs: List<Herb> = emptyList(),
    val isLoading: Boolean = false,
    val event: Event? = null,
) {
    sealed interface Event {
        data class Error(val exception: Exception) : Event
    }
}

enum class DocType(val docType: String) {
    HISTORY(docType = USER_HISTORY_FIELD_NAME),
    FAVORITE(docType = USER_FAVORITE_FIELD_NAME),
}
