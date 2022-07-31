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

package com.uri.lee.dl.camera

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.uri.lee.dl.R
import com.uri.lee.dl.image.MultiObjectProcessor
import com.uri.lee.dl.image.ProminentObjectProcessor
import com.uri.lee.dl.labeling.BottomSheetScrimView
import com.uri.lee.dl.labeling.HerbAdapter
import com.uri.lee.dl.labeling.LabelImage
import com.uri.lee.dl.settings.PreferenceUtils
import com.uri.lee.dl.settings.SettingsActivity
import java.io.IOException

/** Demonstrates the object detection and visual search workflow using camera preview.  */
class CameraActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var searchButton: ExtendedFloatingActionButton? = null
    private var searchButtonAnimator: AnimatorSet? = null
    private var searchProgressBar: ProgressBar? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null
    private var searchEngine: LabelImage? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var productRecyclerView: RecyclerView? = null
    private var bottomSheetTitleView: TextView? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchEngine = LabelImage(applicationContext)

        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@CameraActivity)
            cameraSource = CameraSource(this)
        }
        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                setTarget(promptChip)
            }
        searchButton = findViewById<ExtendedFloatingActionButton>(R.id.product_search_button).apply {
            setOnClickListener(this@CameraActivity)
        }
        searchButtonAnimator =
            (AnimatorInflater.loadAnimator(this, R.animator.search_button_enter) as AnimatorSet).apply {
                setTarget(searchButton)
            }
        searchProgressBar = findViewById(R.id.search_progress_bar)
        setUpBottomSheet()
        findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@CameraActivity)
        }
        settingsButton = findViewById<View>(R.id.settings_button).apply {
            setOnClickListener(this@CameraActivity)
        }
        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        workflowModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(
            if (PreferenceUtils.isMultipleObjectsMode(this)) {
                MultiObjectProcessor(graphicOverlay!!, workflowModel!!)
            } else {
                ProminentObjectProcessor(graphicOverlay!!, workflowModel!!)
            }
        )
        workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
        searchEngine?.shutdown()
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.product_search_button -> {
                searchButton?.isEnabled = false
                workflowModel?.onSearchButtonClicked()
            }
            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
            R.id.close_button -> onBackPressed()
            R.id.flash_button -> {
                if (flashButton?.isSelected == true) {
                    flashButton?.isSelected = false
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                } else {
                    flashButton?.isSelected = true
                    cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                }
            }
            R.id.settings_button -> {
                settingsButton?.isEnabled = false
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.workflowModel ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (workflowModel?.isCameraLive == true) {
            workflowModel!!.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        bottomSheetBehavior?.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Log.d(TAG, "Bottom sheet new state: $newState")
                    bottomSheetScrimView?.visibility =
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    graphicOverlay?.clear()

                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
                        BottomSheetBehavior.STATE_COLLAPSED,
                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false
                        BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val searchedObject = workflowModel!!.detectedBitmapObject.value
                    if (searchedObject == null || java.lang.Float.isNaN(slideOffset)) {
                        return
                    }

                    val graphicOverlay = graphicOverlay ?: return
                    val bottomSheetBehavior = bottomSheetBehavior ?: return
                    val collapsedStateHeight = bottomSheetBehavior.peekHeight.coerceAtMost(bottomSheet.height)
                    val bottomBitmap = objectThumbnailForBottomSheet ?: return
                    if (slidingSheetUpFromHiddenState) {
                        val thumbnailSrcRect = graphicOverlay.translateRect(searchedObject.boundingBox)
                        bottomSheetScrimView?.updateWithThumbnailTranslateAndScale(
                            bottomBitmap,
                            collapsedStateHeight,
                            slideOffset,
                            thumbnailSrcRect
                        )
                    } else {
                        bottomSheetScrimView?.updateWithThumbnailTranslate(
                            bottomBitmap, collapsedStateHeight, slideOffset, bottomSheet
                        )
                    }
                }
            })

        bottomSheetScrimView = findViewById<BottomSheetScrimView>(R.id.bottom_sheet_scrim_view).apply {
            setOnClickListener(this@CameraActivity)
        }

        bottomSheetTitleView = findViewById(R.id.bottom_sheet_title)
        productRecyclerView = findViewById<RecyclerView>(R.id.herb_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@CameraActivity)
            adapter = HerbAdapter(this@CameraActivity, ImmutableList.of())
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java).apply {

            // Observes the workflow state changes, if happens, update the overlay view indicators and
            // camera preview state.
            workflowState.observe(this@CameraActivity, Observer { workflowState ->
                if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                    return@Observer
                }
                currentWorkflowState = workflowState
                Log.d(TAG, "Current workflow state: ${workflowState.name}")

                if (PreferenceUtils.isAutoSearchEnabled(this@CameraActivity)) {
                    stateChangeInAutoSearchMode(workflowState)
                } else {
                    stateChangeInManualSearchMode(workflowState)
                }
            })

            // Observes changes on the object to search, if happens, fire product search request.
            objectToSearch.observe(this@CameraActivity) { detectObject ->
                searchEngine!!.label(detectObject) { detectedObject, products ->
                    workflowModel?.onSearchCompleted(detectedObject, products)
                }
            }

            // Observes changes on the object that has search completed, if happens, show the bottom sheet
            // to present search result.
            detectedBitmapObject.observe(this@CameraActivity, Observer { nullableSearchedObject ->
                val searchedObject = nullableSearchedObject ?: return@Observer
                searchedObject.detectedObject.herbs?.let {
                    objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
                    bottomSheetTitleView?.text = resources
                        .getQuantityString(
                            R.plurals.bottom_sheet_title, it.size, it.size
                        )
                    productRecyclerView?.adapter = HerbAdapter(this@CameraActivity, it)
                    slidingSheetUpFromHiddenState = true
                    bottomSheetBehavior?.peekHeight =
                        preview?.height?.div(2) ?: BottomSheetBehavior.PEEK_HEIGHT_AUTO
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            })
        }
    }

    private fun stateChangeInAutoSearchMode(workflowState: WorkflowModel.WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        searchButton?.visibility = View.GONE
        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(
                    if (workflowState == WorkflowModel.WorkflowState.CONFIRMING)
                        R.string.prompt_hold_camera_steady
                    else
                        R.string.prompt_point_at_an_object
                )
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                searchProgressBar?.visibility = View.VISIBLE
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                stopCameraPreview()
            }
            else -> promptChip?.visibility = View.GONE
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        if (shouldPlayPromptChipEnteringAnimation && promptChipAnimator?.isRunning == false) {
            promptChipAnimator?.start()
        }
    }

    private fun stateChangeInManualSearchMode(workflowState: WorkflowModel.WorkflowState) {
        val wasPromptChipGone = promptChip?.visibility == View.GONE
        val wasSearchButtonGone = searchButton?.visibility == View.GONE

        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            WorkflowModel.WorkflowState.DETECTING, WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_point_at_an_object)
                searchButton?.visibility = View.GONE
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = true
                searchButton?.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = false
                searchButton?.setBackgroundColor(Color.GRAY)
                searchProgressBar!!.visibility = View.VISIBLE
                stopCameraPreview()
            }
            WorkflowModel.WorkflowState.SEARCHED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
                stopCameraPreview()
            }
            else -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.GONE
            }
        }

        val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
        promptChipAnimator?.let {
            if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
        }

        val shouldPlaySearchButtonEnteringAnimation = wasSearchButtonGone && searchButton?.visibility == View.VISIBLE
        searchButtonAnimator?.let {
            if (shouldPlaySearchButtonEnteringAnimation && !it.isRunning) it.start()
        }
    }

    companion object {
        private const val TAG = "LiveObjectActivity"
    }
}
