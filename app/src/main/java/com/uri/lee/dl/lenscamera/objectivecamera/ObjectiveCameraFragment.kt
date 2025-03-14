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

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.uri.lee.dl.R
import com.uri.lee.dl.RECOGNIZED_LATIN_HERBS_KEY
import com.uri.lee.dl.RECOGNIZED_VI_HERBS_KEY
import com.uri.lee.dl.databinding.FragmentObjectiveCameraBinding
import com.uri.lee.dl.labeling.BottomSheetScrimView
import com.uri.lee.dl.labeling.HerbAdapter
import com.uri.lee.dl.lensimage.MultiObjectProcessor
import com.uri.lee.dl.settings.PreferenceUtils
import com.uri.lee.dl.settings.SettingsActivity
import timber.log.Timber
import java.io.IOException


/** Demonstrates the object detection and visual search workflow using camera preview.  */
class ObjectiveCameraFragment(private val confidence: Float) : Fragment(), OnClickListener {

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
    private val objectiveCameraViewModel: ObjectiveCameraViewModel by viewModels()
    private var currentWorkflowState: ObjectiveCameraViewModel.WorkflowState? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var productRecyclerView: RecyclerView? = null
    private var bottomSheetTitleView: TextView? = null
    private var objectThumbnailForBottomSheet: Bitmap? = null
    private var slidingSheetUpFromHiddenState: Boolean = false

    private lateinit var binding: FragmentObjectiveCameraBinding

    companion object {
        fun newInstance(confidence: Float) =
            ObjectiveCameraFragment(confidence = confidence) // keep this so that we can pass args values later if needed
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentObjectiveCameraBinding.inflate(inflater, container, false)
        val intent = requireActivity().intent
        val latinBundle = intent.getBundleExtra(RECOGNIZED_LATIN_HERBS_KEY)
        val viBundle = intent.getBundleExtra(RECOGNIZED_VI_HERBS_KEY)
        objectiveCameraViewModel.setRecognizedHerbs(
            recognizedLatinHerbs = latinBundle!!,
            recognizedViHerbs = viBundle!!
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preview = binding.cameraPreview
        graphicOverlay = binding.cameraPreviewOverlay.cameraPreviewGraphicOverlay.apply {
            setOnClickListener(this@ObjectiveCameraFragment)
            cameraSource = CameraSource(this)
        }
        promptChip = binding.cameraPreviewOverlay.bottomPromptChip
        promptChipAnimator =
            (AnimatorInflater.loadAnimator(
                requireContext(),
                R.animator.bottom_prompt_chip_enter
            ) as AnimatorSet).apply {
                setTarget(promptChip)
            }
        searchButton = binding.cameraPreviewOverlay.productSearchButton.apply {
            setOnClickListener(this@ObjectiveCameraFragment)
        }
        searchButtonAnimator =
            (AnimatorInflater.loadAnimator(requireContext(), R.animator.search_button_enter) as AnimatorSet).apply {
                setTarget(searchButton)
            }
        searchProgressBar = binding.cameraPreviewOverlay.searchProgressBar
        setUpBottomSheet()
        binding.closeButton.setOnClickListener(this)
        flashButton = binding.flashButton.apply { setOnClickListener(this@ObjectiveCameraFragment) }
        settingsButton = binding.settingsButton.apply { setOnClickListener(this@ObjectiveCameraFragment) }
        setUpWorkflowModel()
    }


    override fun onResume() {
        super.onResume()
        objectiveCameraViewModel.markCameraFrozen()
        settingsButton?.isEnabled = true
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        currentWorkflowState = ObjectiveCameraViewModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(MultiObjectProcessor(graphicOverlay!!, objectiveCameraViewModel))
        objectiveCameraViewModel.setWorkflowState(ObjectiveCameraViewModel.WorkflowState.DETECTING)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = ObjectiveCameraViewModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.product_search_button -> {
                searchButton?.isEnabled = false
                objectiveCameraViewModel.onSearchButtonClicked()
            }

            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
            R.id.close_button -> requireActivity().finish()
            R.id.flash_button -> {
                view.apply {
                    isVisible = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                    setOnClickListener {
                        if (view.isSelected) {
                            view.isSelected = false
                            cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                        } else {
                            view.isSelected = true
                            cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                        }
                    }
                }
            }

            R.id.settings_button -> {
                settingsButton?.isEnabled = false
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
    }

    private fun startCameraPreview() {
        val cameraSource = this.cameraSource ?: return
        val workflowModel = this.objectiveCameraViewModel
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Timber.e(e, "Failed to start camera preview!")
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        if (objectiveCameraViewModel.isCameraLive) {
            objectiveCameraViewModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.root)
        bottomSheetBehavior?.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Timber.d("Bottom sheet new state: $newState")
                    bottomSheetScrimView?.visibility =
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    graphicOverlay?.clear()

                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> objectiveCameraViewModel.setWorkflowState(
                            ObjectiveCameraViewModel.WorkflowState.DETECTING
                        )

                        BottomSheetBehavior.STATE_COLLAPSED,
                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> slidingSheetUpFromHiddenState = false

                        BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val searchedObject = objectiveCameraViewModel.detectedBitmapObject.value
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

        bottomSheetScrimView = binding.bottomSheetScrimView.apply {
            setOnClickListener(this@ObjectiveCameraFragment)
        }

        bottomSheetTitleView = binding.bottomSheet.bottomSheetTitle
        productRecyclerView = binding.bottomSheet.herbRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = HerbAdapter(ImmutableList.of())
        }
    }

    private fun setUpWorkflowModel() {
        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        objectiveCameraViewModel.workflowState.observe(viewLifecycleOwner, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }
            currentWorkflowState = workflowState
            Timber.d("Current workflow state: ${workflowState.name}")

            if (PreferenceUtils.isAutoSearchEnabled(requireContext())) {
                stateChangeInAutoSearchMode(workflowState)
            } else {
                stateChangeInManualSearchMode(workflowState)
            }
        })

        // Observes changes on the object to search, if happens, fire product search request.
        objectiveCameraViewModel.objectToSearch.observe(viewLifecycleOwner) { detectObject ->
            objectiveCameraViewModel.label(detectObject, confidence) {
                objectiveCameraViewModel.onSearchCompleted(detectObject, it)
            }
        }

        // Observes changes on the object that has search completed, if happens, show the bottom sheet
        // to present search result.
        objectiveCameraViewModel.detectedBitmapObject.observe(viewLifecycleOwner, Observer { nullableSearchedObject ->
            val searchedObject = nullableSearchedObject ?: return@Observer
            searchedObject.detectedObject.herbs?.let {
                objectThumbnailForBottomSheet = searchedObject.getObjectThumbnail()
                bottomSheetTitleView?.text = resources
                    .getQuantityString(
                        R.plurals.bottom_sheet_title, it.size, it.size
                    )
                productRecyclerView?.adapter = HerbAdapter(it)
                slidingSheetUpFromHiddenState = true
                bottomSheetBehavior?.peekHeight =
                    preview?.height?.div(2) ?: BottomSheetBehavior.PEEK_HEIGHT_AUTO
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        })
    }

    private fun stateChangeInAutoSearchMode(workflowState: ObjectiveCameraViewModel.WorkflowState) {
        val wasPromptChipGone = promptChip!!.visibility == View.GONE

        searchButton?.visibility = View.GONE
        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            ObjectiveCameraViewModel.WorkflowState.DETECTING, ObjectiveCameraViewModel.WorkflowState.DETECTED, ObjectiveCameraViewModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(
                    if (workflowState == ObjectiveCameraViewModel.WorkflowState.CONFIRMING)
                        R.string.prompt_hold_camera_steady
                    else
                        R.string.prompt_point_at_an_object
                )
                startCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.SEARCHING -> {
                searchProgressBar?.visibility = View.VISIBLE
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_searching)
                stopCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.SEARCHED -> {
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

    private fun stateChangeInManualSearchMode(workflowState: ObjectiveCameraViewModel.WorkflowState) {
        val wasPromptChipGone = promptChip?.visibility == View.GONE
        val wasSearchButtonGone = searchButton?.visibility == View.GONE

        searchProgressBar?.visibility = View.GONE
        when (workflowState) {
            ObjectiveCameraViewModel.WorkflowState.DETECTING, ObjectiveCameraViewModel.WorkflowState.DETECTED, ObjectiveCameraViewModel.WorkflowState.CONFIRMING -> {
                promptChip?.visibility = View.VISIBLE
                promptChip?.setText(R.string.prompt_point_at_an_object)
                searchButton?.visibility = View.GONE
                startCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.CONFIRMED -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = true
                searchButton?.setBackgroundColor(Color.WHITE)
                startCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.SEARCHING -> {
                promptChip?.visibility = View.GONE
                searchButton?.visibility = View.VISIBLE
                searchButton?.isEnabled = false
                searchButton?.setBackgroundColor(Color.GRAY)
                searchProgressBar!!.visibility = View.VISIBLE
                stopCameraPreview()
            }

            ObjectiveCameraViewModel.WorkflowState.SEARCHED -> {
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
}
