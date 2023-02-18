package com.uri.lee.dl.lensimage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.*
import com.uri.lee.dl.Utils.loadBitmapFromUri
import com.uri.lee.dl.labeling.BitmapInputInfo
import com.uri.lee.dl.labeling.Herb
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CancellationException

class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val application = getApplication<BaseApplication>()
    private val stateFlow = MutableStateFlow(SingleImageState())

    private var labeler: ImageLabeler? = null
    private var detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
    )

    /** Emits the current state. */
    fun state(): Flow<SingleImageState> = stateFlow

    /** Retrieves the current state. */
    val state: SingleImageState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        setState { copy(isLoading = true) }
        try {
            isObjectsMode()
            getConfidence()
            setState { copy(isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(event = SingleImageState.Event.DataStoreError(e), isLoading = false) }
        }
    }

    private suspend fun isObjectsMode() {
        application.dataStore.data
            .map { settings -> settings[IS_OBJECTS_MODE_SINGLE_IMAGE] ?: true }
            .take(1)
            .collect { isObjectsMode -> setState { copy(isObjectsMode = isObjectsMode) } }
    }

    private suspend fun getConfidence() {
        application.dataStore.data
            .map { settings -> settings[CONFIDENCE_LEVEL] ?: 0.5f }
            .take(1)
            .collect { confidence -> setState { copy(confidence = confidence) } }
    }

    fun setImageUri(imageUri: Uri) {
        Timber.d("setImageUri")
        viewModelScope.launch {
            setState { copy(imageUri = imageUri) }
        }
    }

    fun setObjectsMode(checked: Boolean) {
        Timber.d("setObjectsMode")
        viewModelScope.launch {
            setState { copy(isObjectsMode = checked) }
            application.dataStore.edit { settings -> settings[IS_OBJECTS_MODE_SINGLE_IMAGE] = checked }
            process()
        }
    }

    fun setConfidence(confidence: Float) {
        Timber.d("setConfidence")
        viewModelScope.launch {
            setState { copy(confidence = confidence) }
            application.dataStore.edit { settings -> settings[CONFIDENCE_LEVEL] = confidence }
        }
        process()
    }

    fun process() {
        Timber.d("process")
        if (state.imageUri == null || state.confidence == null || state.isObjectsMode == null) return
        getHerbModel {
            val options = it.setConfidenceThreshold(state.confidence!!).build()
            labeler = ImageLabeling.getClient(options)
            viewModelScope.launch {
                setState {
                    copy(event = null, isLoading = true, objectInfoList = null, entireImageRecognizedHerbs = null)
                }
                try {
                    if (state.isObjectsMode == true) detectObject() else inferEntireImageLabels()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                    setState { copy(event = SingleImageState.Event.Other(e), isLoading = false) }
                }
            }
        }
    }

    private suspend fun inferEntireImageLabels() {
        Timber.d("inferEntireImageLabels")
        val entireBitmap = getBitmapFromFileUri(state.imageUri!!, MAX_IMAGE_DIMENSION_FOR_LABELING) ?: return
        setState { copy(entireBitmap = entireBitmap) }
        val inputImage = InputImage.fromBitmap(entireBitmap, 0)
        labelImage(inputImage = inputImage) { setState { copy(entireImageRecognizedHerbs = it, isLoading = false) } }
    }

    private suspend fun detectObject() {
        Timber.d("detectObject")
        val entireBitmap = getBitmapFromFileUri(state.imageUri!!, MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION) ?: return
        setState { copy(entireBitmap = entireBitmap) }
        detector.process(InputImage.fromBitmap(entireBitmap, 0))
            .addOnSuccessListener { objects ->
                if (objects.isEmpty()) {
                    setState { copy(event = SingleImageState.Event.NoHerbObjects, isLoading = false) }
                } else {
                    var herbObjectsCount = -1
                    val newObjectInfoList = mutableListOf<DetectedObjectInfo>()
                    for (i in objects.indices) {
                        val detectedGeneralObjectInfo = DetectedObjectInfo(
                            detectedObject = objects[i],
                            objectIndex = i,
                            inputInfo = BitmapInputInfo(entireBitmap),
                            herbs = null
                        )
                        labelImage(
                            inputImage = InputImage.fromBitmap(detectedGeneralObjectInfo.getBitmap(), 0),
                        ) { herbs ->
                            if (herbs.isNotEmpty()) {
                                herbObjectsCount++
                                val detectedHerbObjectInfo = DetectedObjectInfo(
                                    detectedObject = objects[herbObjectsCount],
                                    objectIndex = herbObjectsCount,
                                    inputInfo = BitmapInputInfo(entireBitmap),
                                    herbs = herbs
                                )
                                newObjectInfoList.add(detectedHerbObjectInfo)
                            }
                            if (i == objects.size - 1) {
                                if (newObjectInfoList.isEmpty()) {
                                    setState { copy(event = SingleImageState.Event.NoHerbObjects, isLoading = false) }
                                } else {
                                    setState { copy(objectInfoList = newObjectInfoList.toList()) }
                                }
                                setState { copy(isLoading = false) }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                Timber.e(it.message)
                setState { copy(event = SingleImageState.Event.ObjectDetectionError(it), isLoading = false) }
            }
    }

    private suspend fun getBitmapFromFileUri(imageUri: Uri, maxDimension: Int): Bitmap? = try {
        application.loadBitmapFromUri(imageUri, maxDimension)
    } catch (e: IOException) {
        Timber.e(e.message)
        setState { copy(event = SingleImageState.Event.BitmapError(e)) }
        null
    }

    private inline fun labelImage(inputImage: InputImage, crossinline callback: (herbList: List<Herb>) -> Unit) {
        labeler!!.process(inputImage)
            .addOnSuccessListener {
                if (it.isEmpty()) {
                    if (state.isObjectsMode == true) {
                        callback.invoke(emptyList())
                    } else {
                        setState {
                            copy(event = SingleImageState.Event.NoHerbsRecognized, isLoading = false)
                        }
                    }
                    return@addOnSuccessListener
                }
                val maxResultsDisplayed = it.size
                val recognitionList = mutableListOf<Herb>()
                for (i in 0 until maxResultsDisplayed) {
                    val id = it[i].text.substringBefore(" ")
                    recognitionList.add(
                        Herb(
                            id = id,
                            latinName = latinList[id]!!,
                            viName = viList70[id]!!,
                            confidence = it[i].confidence
                        )
                    )
                }
                callback.invoke(recognitionList)
            }
            .addOnFailureListener {
                Timber.e(it.message)
                setState { copy(event = SingleImageState.Event.LabelingError(it), isLoading = false) }
            }
    }

    private inline fun setState(copiedState: SingleImageState.() -> SingleImageState) = stateFlow.update(copiedState)

    override fun onCleared() {
        super.onCleared()
        try {
            detector.close()
            labeler?.close()
        } catch (e: IOException) {
            Timber.e("Failed to close the detector or labeler!")
        }
    }
}

data class SingleImageState(
    val imageUri: Uri? = null,
    val isObjectsMode: Boolean? = null,
    val confidence: Float? = null,
    val entireBitmap: Bitmap? = null,
    val objectInfoList: List<DetectedObjectInfo>? = null,
    val entireImageRecognizedHerbs: List<Herb>? = null,
    val isLoading: Boolean = false,
    val event: Event? = null,
) {
    sealed interface Event {
        data class LabelingError(val exception: Exception) : Event
        data class ObjectDetectionError(val exception: Exception) : Event
        data class BitmapError(val exception: Exception) : Event
        data class DataStoreError(val exception: Exception) : Event
        data class Other(val exception: Exception) : Event
        object NoHerbObjects : Event
        object NoHerbsRecognized : Event
    }
}
