package com.uri.lee.dl.lensimages

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.uri.lee.dl.*
import com.uri.lee.dl.Utils.loadBitmapFromUri
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.lensimages.ImagesState.Recognition
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CancellationException

class ImagesViewModel(application: Application) : AndroidViewModel(application) {

    private val application = getApplication<BaseApplication>()
    private val stateFlow = MutableStateFlow(ImagesState())

    private var labeler: ImageLabeler? = null

    /** Emits the current state. */
    fun state(): Flow<ImagesState> = stateFlow

    /** Retrieves the current state. */
    val state: ImagesState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            getConfidence()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(event = ImagesState.Event.DataStoreError(e)) }
        }
    }

    private suspend fun getConfidence() {
        application.dataStore.data
            .map { settings -> settings[CONFIDENCE_LEVEL] ?: 0.5f }
            .take(1)
            .collect { confidence -> setState { copy(confidence = confidence) } }
    }

    fun setConfidence(confidence: Float) {
        Timber.d("setConfidence")
        viewModelScope.launch {
            setState { copy(confidence = confidence, recognitionList = emptyList()) }
            application.dataStore.edit { settings -> settings[CONFIDENCE_LEVEL] = confidence }
            process(state.imageUris)
        }
    }

    fun clearAllData() {
        Timber.d("clearAllData")
        viewModelScope.launch {
            setState {
                copy(
                    imageUris = emptyList(),
                    event = null,
                    recognitionList = emptyList()
                )
            }
        }
    }

    fun addImageUris(addedUris: List<Uri>) {
        Timber.d("addImageUris")
        if (addedUris.isEmpty()) return
        viewModelScope.launch {
            val currentUriList = state.imageUris.toMutableList()
            currentUriList.addAll(addedUris)
            setState { copy(imageUris = currentUriList) }
            process(addedUris)
        }
    }

    private fun process(uriList: List<Uri>) {
        Timber.d("processCumulatively")
        if (state.confidence == null) return
        getHerbModel {
            val options = it.setConfidenceThreshold(state.confidence!!).build()
            labeler = ImageLabeling.getClient(options)
            viewModelScope.launch {
                setState { copy(event = null) }
                try {
                    labelImages(uriList)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    setState { copy(event = ImagesState.Event.Other(e)) }
                }
            }
        }
    }

    private suspend fun labelImages(addedUris: List<Uri>) {
        addedUris.onEach { uri ->
            val entireBitmap = getBitmapFromFileUri(uri, MAX_IMAGE_DIMENSION_FOR_LABELING) ?: return
            val inputImage = InputImage.fromBitmap(entireBitmap, 0)
            labelSingleImage(inputImage) { labels ->
                val currentRecognitionList = state.recognitionList.toMutableList()
                if (labels.isEmpty()) {
                    currentRecognitionList.add(Recognition(fileUri = uri, herbs = emptyList()))
                } else {
                    val maxResultsDisplayed = labels.size
                    val herbs = mutableListOf<Herb>()
                    for (i in 0 until maxResultsDisplayed) {
                        val id = labels[i].text.substringBefore(" ")
                        herbs.add(
                            Herb(
                                id = id,
                                latinName = latinList[id]!!,
                                viName = viList70[id]!!,
                                confidence = labels[i].confidence
                            )
                        )
                    }
                    currentRecognitionList.add(Recognition(fileUri = uri, herbs = herbs))
                }
                setState { copy(recognitionList = currentRecognitionList) }
            }
        }
    }

    private inline fun labelSingleImage(
        inputImage: InputImage,
        crossinline callback: (imageLabelList: List<ImageLabel>) -> Unit
    ) {
        labeler!!.process(inputImage)
            .addOnSuccessListener { callback.invoke(it) }
            .addOnFailureListener {
                Timber.e(it.message)
                setState { copy(event = ImagesState.Event.LabelingError(it)) }
            }
    }

    private suspend fun getBitmapFromFileUri(imageUri: Uri, maxDimension: Int): Bitmap? = try {
        application.loadBitmapFromUri(imageUri, maxDimension)
    } catch (e: IOException) {
        Timber.e(e.message)
        setState { copy(event = ImagesState.Event.BitmapError(e)) }
        null
    }

    private inline fun setState(copiedState: ImagesState.() -> ImagesState) = stateFlow.update(copiedState)

    override fun onCleared() {
        super.onCleared()
        try {
            labeler?.close()
        } catch (e: IOException) {
            Timber.e("Failed to close the detector or labeler!")
        }
    }
}

data class ImagesState(
    val imageUris: List<Uri> = emptyList(),
    val recognitionList: List<Recognition> = emptyList(),
    val confidence: Float? = null,
    val event: Event? = null,
) {
    data class Recognition(val fileUri: Uri, val herbs: List<Herb>)

    sealed interface Event {
        data class LabelingError(val exception: Exception) : Event
        data class BitmapError(val exception: Exception) : Event
        data class DataStoreError(val exception: Exception) : Event
        data class Other(val exception: Exception) : Event
    }
}
