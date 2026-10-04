package com.uri.lee.dl.lensimage

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.MAX_IMAGE_DIMENSION_FOR_LABELING
import com.uri.lee.dl.MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION
import com.uri.lee.dl.core.ml.MlKitClassifierImage
import com.uri.lee.dl.data.platform.BitmapLoader
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.labeling.BitmapInputInfo
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.toHerbs
import com.uri.lee.dl.lensimage.SingleImageState.Event
import com.uri.lee.dl.core.ui.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.IOException
import kotlin.time.measureTimedValue

sealed interface SingleImageAction {
    data class ImagePicked(val uri: Uri) : SingleImageAction
    data class DetectObjectsChanged(val enabled: Boolean) : SingleImageAction
    data class ConfidenceChanged(val value: Float) : SingleImageAction
}

class ImageViewModel(
    private val recognizeHerbs: RecognizeHerbsUseCase,
    private val settings: SettingsRepository,
    private val bitmaps: BitmapLoader,
) : MviViewModel<SingleImageState, SingleImageAction>(SingleImageState()) {

    // Object detection stays on ML Kit's built-in model; only herb labelling goes through the domain.
    private val detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
    )
    private var processing: Job? = null

    init {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                val saved = settings.scanSettings.first()
                setState {
                    copy(isObjectsMode = saved.detectObjectsInSingleImage, confidence = saved.minConfidence, isLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(event = Event.DataStoreError(e), isLoading = false) }
            }
        }
    }

    override fun onAction(action: SingleImageAction) {
        when (action) {
            is SingleImageAction.ImagePicked -> {
                setState { copy(imageUri = action.uri) }
                process()
            }
            is SingleImageAction.DetectObjectsChanged -> {
                setState { copy(isObjectsMode = action.enabled) }
                viewModelScope.launch { settings.setDetectObjectsInSingleImage(action.enabled) }
                process()
            }
            is SingleImageAction.ConfidenceChanged -> {
                setState { copy(confidence = action.value) }
                viewModelScope.launch { settings.setMinConfidence(action.value) }
                process()
            }
        }
    }

    private fun process() {
        val uri = currentState.imageUri ?: return
        val confidence = currentState.confidence ?: return
        val detectObjects = currentState.isObjectsMode ?: return
        processing?.cancel()
        processing = viewModelScope.launch {
            setState { copy(event = null, isLoading = true, objectInfoList = null, entireImageRecognizedHerbs = null) }
            try {
                if (detectObjects) labelDetectedObjects(uri, confidence) else labelEntireImage(uri, confidence)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MlKitException) {
                Timber.e(e)
                setState { copy(event = Event.LabelingError(e)) }
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(event = Event.Other(e)) }
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    private suspend fun labelEntireImage(uri: Uri, confidence: Float) {
        val (bitmap, bitmapTime) = measureTimedValue { loadBitmap(uri, MAX_IMAGE_DIMENSION_FOR_LABELING) }
        bitmap ?: return
        setState { copy(entireBitmap = bitmap) }
        val (herbs, inferenceTime) = measureTimedValue { label(bitmap, confidence) }
        if (herbs.isEmpty()) {
            setState { copy(event = Event.NoHerbsRecognized) }
            return
        }
        val timed = herbs.map {
            it.copy(bitmapProcessingTime = bitmapTime.inWholeMilliseconds, inferenceProcessingTime = inferenceTime.inWholeMilliseconds)
        }
        setState { copy(entireImageRecognizedHerbs = timed) }
    }

    private suspend fun labelDetectedObjects(uri: Uri, confidence: Float) {
        val bitmap = loadBitmap(uri, MAX_IMAGE_DIMENSION_FOR_OBJECT_DETECTION) ?: return
        setState { copy(entireBitmap = bitmap) }
        val objects = try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            setState { copy(event = Event.ObjectDetectionError(e)) }
            return
        }
        // Keep only objects the model recognises as herbs, each paired with its own detection box.
        val herbObjects = objects.mapNotNull { detected ->
            val crop = DetectedObjectInfo(detected, objectIndex = 0, inputInfo = BitmapInputInfo(bitmap), herbs = null)
            val herbs = label(crop.getBitmap(), confidence)
            herbs.takeIf { it.isNotEmpty() }?.let { detected to it }
        }.mapIndexed { index, (detected, herbs) ->
            DetectedObjectInfo(detected, objectIndex = index, inputInfo = BitmapInputInfo(bitmap), herbs = herbs)
        }
        if (herbObjects.isEmpty()) {
            setState { copy(event = Event.NoHerbObjects) }
        } else {
            setState { copy(objectInfoList = herbObjects) }
        }
    }

    private suspend fun label(bitmap: Bitmap, confidence: Float): List<Herb> =
        recognizeHerbs(MlKitClassifierImage(InputImage.fromBitmap(bitmap, 0)), confidence).toHerbs()


    private suspend fun loadBitmap(uri: Uri, maxDimension: Int): Bitmap? = try {
        bitmaps.load(uri, maxDimension)
    } catch (e: IOException) {
        Timber.e(e)
        setState { copy(event = Event.BitmapError(e)) }
        null
    }

    override fun onCleared() {
        detector.close()
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
        data object NoHerbObjects : Event
        data object NoHerbsRecognized : Event
    }
}
