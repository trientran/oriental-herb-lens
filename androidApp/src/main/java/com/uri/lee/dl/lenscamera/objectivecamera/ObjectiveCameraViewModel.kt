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
import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.uri.lee.dl.data.ml.MlKitClassifierImage
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.labeling.DetectedBitmapObject
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.toHerbs
import com.uri.lee.dl.lensimage.DetectedObjectInfo
import com.uri.lee.dl.settings.PreferenceUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/** View model for handling application workflow based on camera preview.  */
/**
 * Drives the legacy Camera1 object-detection screen, which Phase 3 replaces. Only its labelling
 * moved to [RecognizeHerbsUseCase]; the LiveData workflow is left as it was.
 */
class ObjectiveCameraViewModel(
    private val context: Application,
    private val recognizeHerbs: RecognizeHerbsUseCase,
) : ViewModel() {

    val workflowState = MutableLiveData<WorkflowState>()
    val objectToSearch = MutableLiveData<DetectedObjectInfo>()
    val detectedBitmapObject = MutableLiveData<DetectedBitmapObject>()
    val confidence = MutableLiveData<Float>()
    private val objectIdsToSearch = HashSet<Int>()

    var isCameraLive = false
        private set

    private var confirmedObject: DetectedObjectInfo? = null

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
        viewModelScope.launch {
            try {
                val image = MlKitClassifierImage(InputImage.fromBitmap(detectedObjectInfo.getBitmap(), 0))
                callback(recognizeHerbs(image, confidence).toHerbs())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
            }
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
}
