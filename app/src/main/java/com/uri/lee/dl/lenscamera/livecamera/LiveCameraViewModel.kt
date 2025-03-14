package com.uri.lee.dl.lenscamera.livecamera

import android.os.Bundle
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.lenscamera.objectivecamera.ObjectiveState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutorService

class LiveCameraViewModel : ViewModel() {
    // This is a LiveData field. Choosing this structure because the whole list tend to be updated
    // at once in ML and not individual elements. Updating this once for the entire list makes
    // sense.

    private val stateFlow = MutableStateFlow(ObjectiveState())

    private val _recognitionList = MutableLiveData<List<Herb>>(emptyList())
    val recognitionList: LiveData<List<Herb>> get() = _recognitionList

    private fun updateData(recognitions: List<Herb>) {
        _recognitionList.value = recognitions
    }

    fun analyzeImage(cameraExecutor: ExecutorService, confidence: Float): ImageAnalysis {
        return ImageAnalysis.Builder()
            // This sets the ideal size for the image to be analyse, CameraX will choose the
            // the most suitable resolution which may not be exactly the same or hold the same
            // aspect ratio
            .setTargetResolution(Size(600, 600))
            // How the Image Analyser should pipe in input, 1. every frame but drop no frame, or
            // 2. go to the latest frame and may drop some frame. The default is 2.
            // STRATEGY_KEEP_ONLY_LATEST. The following line is optional, kept here for clarity
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, ImageAnalyzer(
                    confidence = confidence,
                    recognizedLatinHerbs = state.recognizedLatinHerbs,
                    recognizedViHerbs = state.recognizedViHerbs
                ) { recognitionList ->
                    // updating the list of recognised objects
                    updateData(recognitionList)
                })
            }
    }

    /** Emits the current state. */
    fun state(): Flow<ObjectiveState> = stateFlow

    /** Retrieves the current state. */
    val state: ObjectiveState get() = stateFlow.value

    init {
        viewModelScope.launch { stateFlow.collect { Timber.d(it.toString()) } }
    }

    fun setRecognizedHerbs(recognizedLatinHerbs: Bundle, recognizedViHerbs: Bundle) {
        setState { copy(recognizedLatinHerbs = recognizedLatinHerbs, recognizedViHerbs = recognizedViHerbs) }
    }

    private inline fun setState(copiedState: ObjectiveState.() -> ObjectiveState) = stateFlow.update(copiedState)
}
