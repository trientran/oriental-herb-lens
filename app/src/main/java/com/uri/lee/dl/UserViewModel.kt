package com.uri.lee.dl

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException

val TAB_TITLES = arrayOf(R.string.all_herbs, R.string.favorite, R.string.history)
const val PAGING_ITEM_COUNT_ONE_GO = 10L

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
            checkAdminStatus()
            processAllHerbsFirstBatch()
            liveHistoryAndFavoriteUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(error = UserState.Error(e)) }
        }
    }

    private fun processAllHerbsFirstBatch() {
        Timber.d("processAllHerbsFirstBatch")
        viewModelScope.launch {
            try {
                val documentSnapshots = herbCollection
                    .orderBy(if (isSystemLanguageVietnamese) "viName" else "latinName")
                    .limit(PAGING_ITEM_COUNT_ONE_GO)
                    .get()
                    .await()
                val allHerbs = buildList<FireStoreHerb> { documentSnapshots.onEach { add(it.toObject()) } }
                val lastVisibleDocumentSnapshot = documentSnapshots.documents[documentSnapshots.size() - 1]
                setState { copy(allHerbs = allHerbs, lastVisibleDocumentSnapshot = lastVisibleDocumentSnapshot) }
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = UserState.Error(e)) }
            }
        }
    }

    fun processAllHerbsPaging() {
        Timber.d("processAllHerbsPaging")
        viewModelScope.launch {
            try {
                val nextDocumentSnapshots = herbCollection
                    .orderBy(if (isSystemLanguageVietnamese) "viName" else "latinName")
                    .startAfter(state.lastVisibleDocumentSnapshot as DocumentSnapshot)
                    .limit(PAGING_ITEM_COUNT_ONE_GO)
                    .get()
                    .await()
                val nextBatch = buildList<FireStoreHerb> { nextDocumentSnapshots.onEach { add(it.toObject()) } }
                val allHerbs = state.allHerbs.toMutableList()
                allHerbs.addAll(nextBatch)
                val lastVisibleDocumentSnapshot = nextDocumentSnapshots.documents[nextDocumentSnapshots.size() - 1]
                setState { copy(allHerbs = allHerbs, lastVisibleDocumentSnapshot = lastVisibleDocumentSnapshot) }
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = UserState.Error(e)) }
            }
        }
    }

    fun loadSingleHerb(herbId: Long, callback: (FireStoreHerb) -> Unit) {
        Timber.d("loadSingleHerb: $herbId")
        viewModelScope.launch {
            try {
                herbCollection.document(herbId.toString()).get().await().let {
                    it.toObject<FireStoreHerb>()?.let { herb -> callback.invoke(herb) }
                }
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = UserState.Error(e)) }
            }
        }
    }

    fun addHerb(newHerb: FireStoreHerb, onNewHerbAdded: (herbId: Long) -> Unit) {
        Timber.d("addHerb")
        viewModelScope.launch {
            try {
                herbCollection.document(newHerb.id.toString()).set(newHerb).await()
                newHerb.id?.let(onNewHerbAdded)
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = UserState.Error(e)) }
            }
        }
    }

    private fun checkAdminStatus() {
        Timber.d("checkAdminStatus")
        viewModelScope.launch {
            userCollection.document(authUI.auth.uid!!).get().await().let {
                it.getBoolean(USER_IS_ADMIN_FIELD_NAME)?.let { setState { copy(isAdmin = it) } }
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
    val allHerbs: List<FireStoreHerb> = emptyList(),
    val lastVisibleDocumentSnapshot: DocumentSnapshot? = null,
    val favoriteHerbIds: List<Long> = emptyList(),
    val historyHerbIds: List<Long> = emptyList(),
    val isAdmin: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
