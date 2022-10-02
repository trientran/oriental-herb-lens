package com.uri.lee.dl

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException
import kotlin.random.Random

val TAB_TITLES = arrayOf(R.string.tab_favorite, R.string.tab_history, R.string.tab_random)
const val PAGING_ITEM_COUNT_ONE_GO = 10

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val stateFlow = MutableStateFlow(UserState())

    private lateinit var personalListListenerRegistration: ListenerRegistration

    /** Emits the current state. */
    fun state(): Flow<UserState> = stateFlow

    /** Retrieves the current state. */
    val state: UserState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        try {
            processRandom()
            liveHistoryAndFavoriteUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(error = UserState.Error(e)) }
        }
    }

    fun processRandom() {
        Timber.d("processRandom")
        viewModelScope.launch {
            repeat(PAGING_ITEM_COUNT_ONE_GO) {
                val randomId = Random.nextLong(from = 1000, until = 4939)
                val currentHerbIdList = state.randomHerbIds.toMutableList()
                currentHerbIdList.add(randomId)
                setState { copy(randomHerbIds = currentHerbIdList) }
            }
        }
    }

    fun loadSingleHerb(herbId: Long, callback: (FireStoreHerb) -> Unit) {
        Timber.d("loadSingleHerb: $herbId")
        viewModelScope.launch {
            herbCollection.document(herbId.toString()).get().await().let {
                it.toObject<FireStoreHerb>()?.let { herb -> callback.invoke(herb) }
            }
        }
    }

    private fun liveHistoryAndFavoriteUpdate() {
        personalListListenerRegistration = userCollection.document(authUI.auth.uid!!)
            .addSnapshotListener { snapshot, e ->
                viewModelScope.launch {
                    if (e != null) {
                        Timber.e(e)
                        setState { copy(error = UserState.Error(e)) }
                        return@launch
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val historyHerbIds = (snapshot.get(USER_HISTORY_FIELD_NAME) as? List<*>)
                            ?.reversed()
                            ?.mapNotNull { it as? Long }
                        historyHerbIds?.let { setState { copy(historyHerbIds = it) } }
                        val favoriteHerbIds = (snapshot.get(USER_FAVORITE_FIELD_NAME) as? List<*>)
                            ?.reversed()
                            ?.mapNotNull { it as? Long }
                        favoriteHerbIds?.let { setState { copy(favoriteHerbIds = it) } }
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        if (this::personalListListenerRegistration.isInitialized) personalListListenerRegistration.remove()
    }

    private inline fun setState(copiedState: UserState.() -> UserState) = stateFlow.update(copiedState)

}

data class UserState(
    val randomHerbIds: List<Long> = emptyList(),
    val favoriteHerbIds: List<Long> = emptyList(),
    val historyHerbIds: List<Long> = emptyList(),
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
