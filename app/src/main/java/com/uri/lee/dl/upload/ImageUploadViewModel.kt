package com.uri.lee.dl.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


class ImageUploadViewModel : ViewModel() {

    private val stateFlow = MutableStateFlow(ImageUploadState())

    fun state(): Flow<ImageUploadState> = stateFlow

    val state: ImageUploadState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun clearAllData() {
        Timber.d("clearAllData")
        viewModelScope.launch {
            setState { copy(imageUris = emptyList(), event = null) }
        }
    }

    fun addImageUris(addedUris: List<Uri>) {
        Timber.d("addImageUris")
        if (addedUris.isEmpty()) return
        viewModelScope.launch {
            val currentUriList = state.imageUris.toMutableList()
            currentUriList.addAll(addedUris)
            setState { copy(imageUris = currentUriList) }
        }
    }

    fun setHerbId(herbId: String) {
        viewModelScope.launch { setState { copy(herbId = herbId) } }
    }


    fun upload() {
//        val uploadTask = herbStorage.child(state.herbId!!).putFile(state.imageUris)
//
//// Register observers to listen for when the download is done or if it fails
//        uploadTask.addOnFailureListener {
//            // Handle unsuccessful uploads
//        }.addOnSuccessListener { taskSnapshot ->
//            // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
//            // ...
//        }
    }

    private inline fun setState(copiedState: ImageUploadState.() -> ImageUploadState) = stateFlow.update(copiedState)

}

data class ImageUploadState(
    val herbId: String? = null,
    val imageUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val event: Event? = null,
) {
    sealed interface Event {
        data class Error(val exception: Exception) : Event
    }
}
