package com.uri.lee.dl.herbdetails

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.herbdetails.images.ImageDeleteReason
import com.uri.lee.dl.instantsearch.Herb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException

class HerbDetailsViewModel : ViewModel() {

    private lateinit var herbListenerRegistration: ListenerRegistration
    private lateinit var userListenerRegistration: ListenerRegistration
    private lateinit var urisListenerRegistration: ListenerRegistration

    private val stateFlow = MutableStateFlow(HerbDetailsState())

    fun state(): Flow<HerbDetailsState> = stateFlow

    val state: HerbDetailsState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun setId(objectID: String) {
        Timber.d("setId")
        viewModelScope.launch {
            setState { copy(herb = Herb(objectID = objectID)) }
            liveHerbUpdate(objectID)
            liveLikeListUpdate()
        }
    }

    fun deleteImage(uri: Uri, uid: String, deleteReason: ImageDeleteReason) {
        Timber.d("deleteImage")
        globalScope.launch {
            try {
                val deleteReasonString = when (deleteReason) {
                    ImageDeleteReason.FaultyImage -> "Faulty image"
                    ImageDeleteReason.DuplicatedImage -> "Duplicated Image"
                    else -> "Other" // Need to find out why this requires else branch
                }
                deletionCollection.add(
                    ImageDeletionRequest(
                        url = uri.toString(),
                        uid = uid,
                        herbId = state.herb!!.id!!,
                        reason = deleteReasonString,
                        requestedBy = authUI.auth.uid!!,
                    )
                )
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun setLike() {
        Timber.d("setLike")
        viewModelScope.launch {
            authUI.auth.uid!!.let {
                setState { copy(isLiked = !state.isLiked) }
                if (state.isLiked) {
                    userCollection
                        .document(it)
                        .update(USER_FAVORITE_FIELD_NAME, FieldValue.arrayUnion(state.herb!!.objectID)).await()
                    Timber.d("New like successfully written!")
                } else {
                    userCollection
                        .document(it)
                        .update(USER_FAVORITE_FIELD_NAME, FieldValue.arrayRemove(state.herb!!.objectID)).await()
                    Timber.d("Like successfully removed!")
                }
            }
        }
    }

    private fun liveHerbUpdate(objectID: String) {
        herbCollection.document(objectID).apply {
            herbListenerRegistration = this.addSnapshotListener { snapshot, e ->
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

    private fun liveLikeListUpdate() {
        userListenerRegistration = userCollection.document(authUI.auth.uid!!).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Timber.e(e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    snapshot.toLikes()?.apply {
                        viewModelScope.launch {
                            val isLiked = snapshot.toLikes()!!.contains(state.herb!!.objectID)
                            setState { copy(isLiked = isLiked) }
                        }
                    }

                    snapshot.toHistory().apply {
                        viewModelScope.launch {
                            val history = this@apply.toMutableList()
                            if (state.herb!!.objectID.count() >= 4) { // herb id must be at least 4 characters)
                                history.remove(state.herb!!.objectID)
                                history.add(state.herb!!.objectID)
                                val data = hashMapOf(USER_HISTORY_FIELD_NAME to history)
                                userCollection.document(authUI.auth.uid!!).set(data, SetOptions.merge())
                                Timber.d("Successfully added to history!")
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    setState { copy(error = HerbDetailsState.Error(e)) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (this::herbListenerRegistration.isInitialized) herbListenerRegistration.remove()
        if (this::userListenerRegistration.isInitialized) userListenerRegistration.remove()
        if (this::urisListenerRegistration.isInitialized) urisListenerRegistration.remove()
    }

    private inline fun setState(copiedState: HerbDetailsState.() -> HerbDetailsState) = stateFlow.update(copiedState)
}

data class HerbDetailsState(
    val herb: Herb? = null,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}

data class ImageDeletionRequest(
    val url: String,
    val uid: String,
    val herbId: String,
    val reason: String,
    val requestedBy: String,
)
