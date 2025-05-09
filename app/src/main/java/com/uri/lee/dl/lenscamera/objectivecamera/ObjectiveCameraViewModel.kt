/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uri.lee.dl.lenscamera.objectivecamera

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.uri.lee.dl.getHerbModel
import com.uri.lee.dl.labeling.DetectedBitmapObject
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.lensimage.DetectedObjectInfo
import com.uri.lee.dl.settings.PreferenceUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CancellationException

/** View model for handling application workflow based on camera preview.  */
class ObjectiveCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val stateFlow = MutableStateFlow(ObjectiveState())

    val workflowState = MutableLiveData<WorkflowState>()
    val objectToSearch = MutableLiveData<DetectedObjectInfo>()
    val detectedBitmapObject = MutableLiveData<DetectedBitmapObject>()
    val confidence = MutableLiveData<Float>()
    private var labeler: ImageLabeler? = null

    private val objectIdsToSearch = HashSet<Int>()

    var isCameraLive = false
        private set

    private var confirmedObject: DetectedObjectInfo? = null

    private val context: Context
        get() = getApplication<Application>().applicationContext

    /**
     * State set of the application workflow.
     */
    enum class WorkflowState {
        NOT_STARTED,
        DETECTING,
        DETECTED,
        CONFIRMING,
        CONFIRMED,
        SEARCHING,
        SEARCHED
    }

    @MainThread
    fun setWorkflowState(workflowState: WorkflowState) {
        if (workflowState != WorkflowState.CONFIRMED &&
            workflowState != WorkflowState.SEARCHING &&
            workflowState != WorkflowState.SEARCHED
        ) {
            confirmedObject = null
        }
        this.workflowState.value = workflowState
    }

    @MainThread
    fun confirmingObject(confirmingObject: DetectedObjectInfo, progress: Float) {
        val isConfirmed = progress.compareTo(1f) == 0
        if (isConfirmed) {
            confirmedObject = confirmingObject
            if (PreferenceUtils.isAutoSearchEnabled(context)) {
                setWorkflowState(WorkflowState.SEARCHING)
                triggerSearch(confirmingObject)
            } else {
                setWorkflowState(WorkflowState.CONFIRMED)
            }
        } else {
            setWorkflowState(WorkflowState.CONFIRMING)
        }
    }

    @MainThread
    fun onSearchButtonClicked() {
        confirmedObject?.let {
            setWorkflowState(WorkflowState.SEARCHING)
            triggerSearch(it)
        }
    }

    private fun triggerSearch(detectedObject: DetectedObjectInfo) {
        val objectId = detectedObject.objectId ?: throw NullPointerException()
        if (objectIdsToSearch.contains(objectId)) {
            // Already in searching.
            return
        }

        objectIdsToSearch.add(objectId)
        objectToSearch.value = detectedObject
    }

    fun markCameraLive() {
        isCameraLive = true
        objectIdsToSearch.clear()
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }

    fun label(detectedObjectInfo: DetectedObjectInfo, confidence: Float, callback: (List<Herb>) -> Unit) {
        getHerbModel {
            val options = it.setConfidenceThreshold(confidence).build()
            labeler = ImageLabeling.getClient(options)
            viewModelScope.launch {
                try {
                    labelObject(detectedObjectInfo, callback)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    private fun labelObject(detectedObjectInfo: DetectedObjectInfo, callback: (List<Herb>) -> Unit) {
        val inputImage = InputImage.fromBitmap(detectedObjectInfo.getBitmap(), 0)
        labeler!!.process(inputImage)
            .addOnSuccessListener {
                if (it.isEmpty()) {
                    callback.invoke(emptyList())
                    return@addOnSuccessListener
                }
                val maxResultsDisplayed = it.size
                val recognitionList = mutableListOf<Herb>()
                for (i in 0 until maxResultsDisplayed) {
                    val id = it[i].text
                    recognitionList.add(
                        Herb(
                            id = id,
                            latinName = state.recognizedLatinHerbs!!.getString(id),
                            viName = state.recognizedViHerbs!!.getString(id),
                            confidence = it[i].confidence
                        )
                    )
                }
                callback.invoke(recognitionList)
            }
            .addOnFailureListener {
                Timber.e(it.message)
            }
    }

    fun onSearchCompleted(detectedObject: DetectedObjectInfo, herbs: List<Herb>) {
        val lConfirmedObject = confirmedObject
        if (detectedObject != lConfirmedObject) {
            // Drops the search result from the object that has lost focus.
            return
        }

        objectIdsToSearch.remove(detectedObject.objectId)
        setWorkflowState(WorkflowState.SEARCHED)

        val withHerbs = lConfirmedObject.copy(herbs = herbs)
        this.detectedBitmapObject.value = DetectedBitmapObject(context.resources, withHerbs)
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

data class ObjectiveState(
    val recognizedLatinHerbs: Bundle? = null, // herbId, latin name
    val recognizedViHerbs: Bundle? = null, // HerbId, viet name
)
