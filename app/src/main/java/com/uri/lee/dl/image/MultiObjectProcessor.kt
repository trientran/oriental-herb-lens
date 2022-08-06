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

package com.uri.lee.dl.image

import android.graphics.PointF
import android.util.Log
import android.util.SparseArray
import androidx.annotation.MainThread
import androidx.core.util.forEach
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.R
import com.uri.lee.dl.camera.objectivecamera.CameraReticleAnimator
import com.uri.lee.dl.camera.objectivecamera.FrameProcessorBase
import com.uri.lee.dl.camera.objectivecamera.GraphicOverlay
import com.uri.lee.dl.camera.objectivecamera.ObjectiveCameraViewModel
import com.uri.lee.dl.labeling.InputInfo
import java.io.IOException
import kotlin.math.hypot

/** A processor to run object detector in multi-objects mode.  */
class MultiObjectProcessor(
    graphicOverlay: GraphicOverlay,
    private val objectiveCameraViewModel: ObjectiveCameraViewModel,
) :
    FrameProcessorBase<List<DetectedObject>>() {
    private val confirmationController: ObjectConfirmationController = ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val objectSelectionDistanceThreshold: Int = graphicOverlay
        .resources
        .getDimensionPixelOffset(R.dimen.object_selection_distance_threshold)
    private val detector: ObjectDetector

    // Each new tracked object plays appearing animation exactly once.
    private val objectDotAnimatorArray = SparseArray<ObjectDotAnimator>()

    init {
        val options: ObjectDetectorOptionsBase
            val optionsBuilder = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            options = optionsBuilder.build()

        this.detector = ObjectDetection.getClient(options)
    }

    override fun stop() {
        super.stop()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    @MainThread
    override fun onSuccess(
        inputInfo: InputInfo,
        results: List<DetectedObject>,
        graphicOverlay: GraphicOverlay
    ) {
        if (!objectiveCameraViewModel.isCameraLive) {
            return
        }

        removeAnimatorsFromUntrackedObjects(results)

        graphicOverlay.clear()

        var selectedObject: DetectedObjectInfo? = null
        for (i in results.indices) {
            val result = results[i]
            if (selectedObject == null && shouldSelectObject(graphicOverlay, result)) {
                selectedObject = DetectedObjectInfo(result, i, inputInfo, null)
                // Starts the object confirmation once an object is regarded as selected.
                confirmationController.confirming(result.trackingId)
                graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))

                graphicOverlay.add(
                    ObjectGraphicInMultiMode(
                        graphicOverlay, selectedObject, confirmationController
                    )
                )
            } else {
                if (confirmationController.isConfirmed) {
                    // Don't render other objects when an object is in confirmed state.
                    continue
                }

                val trackingId = result.trackingId ?: return
                val objectDotAnimator = objectDotAnimatorArray.get(trackingId) ?: let {
                    ObjectDotAnimator(graphicOverlay).apply {
                        start()
                        objectDotAnimatorArray.put(trackingId, this)
                    }
                }
                graphicOverlay.add(
                    ObjectDotGraphic(
                        graphicOverlay, DetectedObjectInfo(result, i, inputInfo, null), objectDotAnimator
                    )
                )
            }
        }

        if (selectedObject == null) {
            confirmationController.reset()
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            cameraReticleAnimator.cancel()
        }

        graphicOverlay.invalidate()

        if (selectedObject != null) {
            objectiveCameraViewModel.confirmingObject(selectedObject, confirmationController.progress)
        } else {
            objectiveCameraViewModel.setWorkflowState(
                if (results.isEmpty()) {
                    ObjectiveCameraViewModel.WorkflowState.DETECTING
                } else {
                    ObjectiveCameraViewModel.WorkflowState.DETECTED
                }
            )
        }
    }

    private fun removeAnimatorsFromUntrackedObjects(detectedObjects: List<DetectedObject>) {
        val trackingIds = detectedObjects.mapNotNull { it.trackingId }
        // Stop and remove animators from the objects that have lost tracking.
        val removedTrackingIds = ArrayList<Int>()
        objectDotAnimatorArray.forEach { key, value ->
            if (!trackingIds.contains(key)) {
                value.cancel()
                removedTrackingIds.add(key)
            }
        }
        removedTrackingIds.forEach {
            objectDotAnimatorArray.remove(it)
        }
    }

    private fun shouldSelectObject(graphicOverlay: GraphicOverlay, visionObject: DetectedObject): Boolean {
        // Considers an object as selected when the camera reticle touches the object dot.
        val box = graphicOverlay.translateRect(visionObject.boundingBox)
        val objectCenter = PointF((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        val reticleCenter = PointF(graphicOverlay.width / 2f, graphicOverlay.height / 2f)
        val distance =
            hypot((objectCenter.x - reticleCenter.x).toDouble(), (objectCenter.y - reticleCenter.y).toDouble())
        return distance < objectSelectionDistanceThreshold
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {

        private const val TAG = "MultiObjectProcessor"
    }
}
