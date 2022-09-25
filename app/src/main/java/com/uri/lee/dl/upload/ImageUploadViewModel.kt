package com.uri.lee.dl.upload

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.SetOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.Utils.compressToJpgByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.CancellationException

private const val MAX_IMAGE_DIMENSION = 600

class ImageUploadViewModel(application: Application) : AndroidViewModel(application) {
    private val application = getApplication<BaseApplication>()
    private val stateFlow = MutableStateFlow(ImageUploadState())
    private val imageApi = RetrofitHelper.getInstance().create(ImageApi::class.java)

    fun state(): Flow<ImageUploadState> = stateFlow

    val state: ImageUploadState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun clearAllData() {
        Timber.d("clearAllData")
        viewModelScope.launch {
            setState { copy(imageUris = emptyList(), error = null) }
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

    fun uploadSequentially() {
        Timber.d("uploadSequentially")
        globalScope.launch {
            val uid = authUI.auth.uid ?: return@launch
            state.herbId ?: return@launch
            val urlMap = mutableMapOf<String, Any>() // url string, uid string
            setState { copy(isUploadComplete = false) }
            try {
                state.imageUris.onEach { uri ->
                    val byteArray = application.compressToJpgByteArray(uri, MAX_IMAGE_DIMENSION)
                    // should use NO_WRAP to make sure there is no break line in the string create a map of data to pass along
                    val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    imageApi.uploadImage(base64String).body()?.let { urlMap[it.image.url] = uid }
                }
                if (urlMap.isNotEmpty()) {
                    herbCollection
                        .document(state.herbId.toString())
                        .set(mapOf(IMAGE_UPLOAD_PATH_NAME to urlMap), SetOptions.merge()).await()
                }
                setState { copy(isUploadComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = ImageUploadState.Error(e)) }
            }
        }
    }

    fun setHerbId(herbId: Long) {
        viewModelScope.launch { setState { copy(herbId = herbId) } }
    }

    private inline fun setState(copiedState: ImageUploadState.() -> ImageUploadState) = stateFlow.update(copiedState)

}

data class ImageUploadState(
    val herbId: Long? = null,
    val imageUris: List<Uri> = emptyList(),
    val isUploadComplete: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: Error? = null,
) {
    data class Error(val exception: Exception)
}
