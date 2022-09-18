package com.uri.lee.dl.herbdetails

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.herbdetails.HerbDetailsState.ImageInfo
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

    private var likeList = mutableListOf<String>()

    private val stateFlow = MutableStateFlow(HerbDetailsState())

    fun state(): Flow<HerbDetailsState> = stateFlow

    val state: HerbDetailsState get() = stateFlow.value

    fun setId(objectID: String) {
        Timber.d("setId")
        viewModelScope.launch {
            setState { copy(herb = Herb(objectID = objectID)) }
            liveHerbUpdate(objectID)
            liveLikeListUpdate()
            //  getImageUris(objectID)
        }
    }

    private fun getImageUris(herbId: String) {
        Log.d("trienn,", herbId)
        val query = uploadCollection.whereEqualTo("herbId", herbId)
        urisListenerRegistration = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Timber.w("Listen failed.", e)
                return@addSnapshotListener
            }
            Log.d("trienn,", snapshot!!.documents.toString())

            snapshot?.let {
                val imageInfoList = mutableListOf<ImageInfo>()
                snapshot.documents.onEach { imageInfoList.addAll(it.toImageInfoList()) }
                viewModelScope.launch { setState { copy(imageInfoList = imageInfoList) } }
            }
        }
    }

    fun setHerb(herb: Herb) {
        Timber.d("setHerb")
        viewModelScope.launch { setState { copy(herb = herb) } }
    }

    fun deleteImage(uri: Uri, uid: String, deleteReason: ImageDeleteReason) {
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
        viewModelScope.launch {
            authUI.auth.uid!!.let {
                setState { copy(isLiked = !state.isLiked) }
                if (state.isLiked) {
                    likeList.add(state.herb!!.objectID)
                    val data = hashMapOf(USER_FAVORITE_FIELD_NAME to likeList)
                    userCollection.document(it)
                        .set(data, SetOptions.merge())
                        .addOnSuccessListener { Timber.d("New like successfully written!") }
                        .addOnFailureListener { e -> Timber.e(e) }
                } else {
                    likeList.remove(state.herb!!.objectID)
                    val data = hashMapOf(USER_FAVORITE_FIELD_NAME to likeList)
                    userCollection.document(it)
                        .set(data, SetOptions.merge())
                        .addOnSuccessListener { Timber.d("Like successfully removed!") }
                        .addOnFailureListener { e -> Timber.e(e) }
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

    private fun DocumentSnapshot.toImageInfoList(): List<ImageInfo> {
        val imageInfoList = mutableListOf<ImageInfo>()
        (get("urls") as? List<*>)!!.onEach {
            it as String
            imageInfoList.add(ImageInfo(url = it, uid = getString("uid").toString()))
        }
        return imageInfoList
    }

    private fun liveLikeListUpdate() {
        userCollection.document(authUI.auth.uid!!).apply {
            userListenerRegistration = this.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Timber.e(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    snapshot.toLikes()?.apply {
                        likeList.clear()
                        onEach { likeList.add(it.toString()) }
                        val isLiked = snapshot.toLikes()?.contains(state.herb!!.objectID) ?: false
                        viewModelScope.launch { setState { copy(isLiked = isLiked) } }
                    }
                    snapshot.toHistory().apply {
                        val history = this.toMutableList()
                        if (state.herb!!.objectID.count() >= 4) { // herb id must be at least 4 characters)
                            history.remove(state.herb!!.objectID)
                            history.add(state.herb!!.objectID)
                            val data = hashMapOf(USER_HISTORY_FIELD_NAME to history)
                            userCollection.document(authUI.auth.uid!!)
                                .set(data, SetOptions.merge())
                                .addOnSuccessListener { Timber.d("Successfully added to history!") }
                                .addOnFailureListener { e -> Timber.e(e) }
                        }
                    }
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
    val imageInfoList: List<ImageInfo> = emptyList(),
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val event: Event? = null,
) {
    data class ImageInfo(
        val url: String,
        val uid: String,
    )

    sealed interface Event {
        data class Error(val exception: Exception) : Event
    }
}

data class ImageDeletionRequest(
    val url: String,
    val uid: String,
    val herbId: String,
    val reason: String,
    val requestedBy: String,
)


