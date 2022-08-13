package com.uri.lee.dl.hometabs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.uri.lee.dl.*
import com.uri.lee.dl.instantsearch.Herb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException
import kotlin.random.Random

val TAB_TITLES = arrayOf(R.string.tab_favorite, R.string.tab_history, R.string.tab_random)

class PageViewModel(application: Application) : AndroidViewModel(application) {
    private val stateFlow = MutableStateFlow(PageState())
    private val application = getApplication<BaseApplication>()

    /** Emits the current state. */
    fun state(): Flow<PageState> = stateFlow

    /** Retrieves the current state. */
    val state: PageState get() = stateFlow.value

    fun setIndex(index: Int) {
        Timber.d("setIndex")
        viewModelScope.launch {
            setState { copy(index = index) }
            try {
                if (authUI.auth.currentUser?.email == null) return@launch
                when (TAB_TITLES[index]) {
                    R.string.tab_favorite -> processHistoryOrFavorite(
                        numberOfRecyclerViewItemsLoaded = 0,
                        numberOfDocumentsToLoad = 10,
                        docType = DocType.FAVORITE
                    )
                    R.string.tab_history -> processHistoryOrFavorite(
                        numberOfRecyclerViewItemsLoaded = 0,
                        numberOfDocumentsToLoad = 10,
                        docType = DocType.HISTORY
                    )
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
                val randomInt = Random.nextInt(from = 1000, until = 3939)
                val randomId = "h${randomInt}"
                herbCollection.document(randomId).get().addOnSuccessListener {
                    val currentHerbList = state.herbs.toMutableList()
                    currentHerbList.add(it.toHerb())
                    setState { copy(herbs = currentHerbList) }
                    if (index == numberOfDocumentsToLoad - 1) setState { copy(isLoading = false) }
                }
                    .addOnFailureListener {
                        Timber.e(it.message)
                        setState { copy(isLoading = false) }
                    }
            }
        }
        if (!application.isNetworkAvailable()) setState { copy(isLoading = false) }
    }

    fun processHistoryOrFavorite(
        numberOfRecyclerViewItemsLoaded: Int = 0,
        numberOfDocumentsToLoad: Int = 10,
        docType: DocType
    ) {
        viewModelScope.launch {
            setState { copy(numberOfHistoryOrFavoriteDocsLoaded = numberOfRecyclerViewItemsLoaded, isLoading = true) }
            userCollection.document(authUI.auth.currentUser!!.email!!).get()
                .addOnSuccessListener { user ->
                    (user.get(docType.docType) as? List<*>)?.reversed()?.apply {
                        var numberOfDocsLoaded = state.numberOfHistoryOrFavoriteDocsLoaded
                        var numberOfNewDocsLoaded = 0
                        while (numberOfDocsLoaded < size) {
                            numberOfNewDocsLoaded++
                            if (numberOfNewDocsLoaded >= numberOfDocumentsToLoad) {
                                setState { copy(isLoading = false) }
                                return@addOnSuccessListener
                            }
                            val currentHerbId = get(numberOfDocsLoaded) as String
                            herbCollection.document(currentHerbId).get()
                                .addOnSuccessListener {
                                    val currentHerbList = state.herbs.toMutableList()
                                    currentHerbList.add(it.toHerb())
                                    setState { copy(herbs = currentHerbList) }
                                    numberOfDocsLoaded++
                                    if (numberOfDocsLoaded == size) setState { copy(isLoading = false) }
                                }
                                .addOnFailureListener {
                                    Timber.e(it.message)
                                    setState { copy(isLoading = false) }
                                }
                        }
                    }
                }
                .addOnFailureListener {
                    Timber.e(it.message)
                    setState { copy(isLoading = false) }
                }
            if (!application.isNetworkAvailable()) setState { copy(isLoading = false) }
        }
    }

    private inline fun setState(copiedState: PageState.() -> PageState) = stateFlow.update(copiedState)

}

fun DocumentSnapshot.toHerb() = Herb(
    objectID = id,
    id = id,
    enDosing = getString("enDosing"),
    enInteractions = getString("enInteractions"),
    enName = getString("enName"),
    enOverview = getString("enOverview"),
    enSideEffects = getString("enSideEffects"),
    latinName = getString("latinName"),
    viDosing = getString("viDosing"),
    viInteractions = getString("viInteractions"),
    viName = getString("viName"),
    viOverview = getString("viOverview"),
    viSideEffects = getString("viSideEffects"),
)

data class PageState(
    val index: Int? = null,
    val herbs: List<Herb> = emptyList(),
    val isLoading: Boolean = false,
    val numberOfHistoryOrFavoriteDocsLoaded: Int = 0,
    val event: Event? = null,
) {
    sealed interface Event {
        data class Error(val exception: Exception) : Event
    }
}

enum class DocType(val docType: String) {
    HISTORY(docType = "History"),
    FAVORITE(docType = "Favorite"),
}

