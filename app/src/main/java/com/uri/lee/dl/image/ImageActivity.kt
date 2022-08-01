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

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.common.collect.ImmutableList
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageBinding
import com.uri.lee.dl.labeling.DetectedBitmapObject
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.HerbAdapter
import com.uri.lee.dl.labeling.PreviewCardAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.set

/** Demonstrates the object detection and visual search workflow using static image.  */
class ImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private val detectedBitmapObjectMap = TreeMap<Int, DetectedBitmapObject>()
    private val viewModel: ImageViewModel by viewModels()
    private var snackbar: Snackbar? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    private var detectedBitmapObjectForBottomSheet: DetectedBitmapObject? = null
    private var dotViewSize: Int = 0
    private var detectedObjectNum = 0
    private var currentSelectedObjectIndex = 0

    private val browseImagesTimer = object : CountDownTimer(5000, 5000) {
        override fun onTick(millisUntilFinished: Long) {
            binding.pickImageView.isEnabled = false
            binding.actionBar.photoLibraryButton.isEnabled = false
            resultLauncher.launch("image/*")
        }

        override fun onFinish() {
            binding.pickImageView.isEnabled = true
            binding.actionBar.photoLibraryButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        dotViewSize = resources.getDimensionPixelOffset(R.dimen.static_image_dot_view_size)

        binding.cardRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ImageActivity, RecyclerView.HORIZONTAL, false)
            addItemDecoration(CardItemDecoration(resources))
        }

        setUpBottomSheet()

        binding.actionBar.closeButton.setOnClickListener { finish() }
        binding.pickImageView.setOnClickListener { browseImagesTimer.start() }
        binding.actionBar.photoLibraryButton.setOnClickListener { browseImagesTimer.start() }

        binding.bottomPromptChip.setOnClickListener { it.isVisible = false }

        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                this.setUpComponents()
            }
        }
    }

    private fun CoroutineScope.setUpComponents() {
        // objects mode switch
        viewModel.state()
            .mapNotNull { it.isObjectsMode }
            .take(1)
            .onEach { binding.actionBar.objectsModeSwitch.isChecked = it }
            .launchIn(this)
        binding.actionBar.objectsModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setObjectsMode(isChecked)
            binding.actionBar.objectsModeSwitch.text =
                if (isChecked) getString(R.string.objects_mode) else getString(R.string.whole_image_mode)
        }
        // seek bar
        viewModel.state()
            .mapNotNull { it.confidence }
            .take(1)
            .onEach {
                binding.actionBar.seekView.seekBar.setProgress((it * 100).toInt(), false)
                binding.actionBar.seekView.confidencePercentView.text =
                    "${binding.actionBar.seekView.seekBar.progress} %"
            }.launchIn(this)
        binding.actionBar.seekView.seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                binding.actionBar.seekView.confidencePercentView.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                viewModel.setConfidence((seekBar.progress.toFloat() / 100))
            }
        })

        viewModel.state()
            .map { it.isLoading }
            .distinctUntilChanged()
            .onEach { binding.loadingView.isVisible = it }
            .launchIn(this)

        viewModel.state()
            .map { it.imageUri }
            .distinctUntilChanged()
            .onEach {
                Glide.with(this@ImageActivity).load(it).into(binding.inputImageView)
                binding.pickImageView.isVisible = it == null
                binding.actionBar.seekView.root.isVisible = it != null
                binding.actionBar.objectsModeSwitch.isVisible = it != null
            }
            .launchIn(this)

        viewModel.state()
            .map { it.isObjectsMode == true && !it.objectInfoList.isNullOrEmpty() }
            .distinctUntilChanged()
            .onEach {
                binding.bottomPromptChip.isVisible = it
                binding.cardRecyclerView.isVisible = it
                binding.dotViewContainer.isVisible = it
            }
            .launchIn(this)

        viewModel.state()
            .map { it.entireImageRecognizedHerbs }
            .distinctUntilChanged()
            .onEach { it?.let { herbs -> showEntireImageLabelingResults(herbs) } }
            .launchIn(this)

        viewModel.state()
            .mapNotNull { it.objectInfoList }
            .distinctUntilChanged()
            .onEach { detectionList ->
                viewModel.state.entireBitmap?.let { bitmap ->
                    onObjectsDetected(inputBitmap = bitmap, objects = detectionList)
                }
            }
            .launchIn(this)

        viewModel.state()
            .mapNotNull { it.imageUri }
            .take(1)
            .onEach { viewModel.process() }
            .launchIn(this)

        viewModel.state()
            .map { it.event }
            .onEach {
                when (it) {
                    is SingleImageState.Event.BitmapError -> showSnackBar(getString(R.string.failed_to_load_file_please_try_again))
                    is SingleImageState.Event.LabelingError -> showSnackBar(getString(R.string.failed_to_label_entire_image_please_turn_on_objects_mode))
                    is SingleImageState.Event.ObjectDetectionError -> showSnackBar(getString(R.string.static_image_detected_no_results_continue_to_label))
                    SingleImageState.Event.NoHerbObjects -> showSnackBar(getString(R.string.no_herb_objects_result_please_try_turning_off_objects_mode))
                    SingleImageState.Event.NoHerbsRecognized -> showSnackBar(getString(R.string.no_herb_result_please_try_turning_on_objects_mode))
                    is SingleImageState.Event.DataStoreError -> showSnackBar(getString(R.string.failed_to_load_local_data_store))
                    null -> snackbar?.dismiss()
                    is SingleImageState.Event.Other -> showSnackBar(getString(R.string.something_went_wrong_please_try_again_or_contact_us))
                }
            }
            .launchIn(this)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { it?.apply { viewModel.setImageUri(this) } }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet)).apply {
            setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        Log.d(TAG, "Bottom sheet new state: $newState")
                        binding.bottomSheetScrimView.visibility =
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        if (java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val collapsedStateHeight = bottomSheetBehavior!!.peekHeight.coerceAtMost(bottomSheet.height)
                        val searchedObjectForBottomSheet = detectedBitmapObjectForBottomSheet
                            ?: return
                        binding.bottomSheetScrimView.updateWithThumbnailTranslate(
                            searchedObjectForBottomSheet.getObjectThumbnail(),
                            collapsedStateHeight,
                            slideOffset,
                            bottomSheet
                        )
                    }
                }
            )
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.bottomSheetScrimView.setOnClickListener {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
        binding.bottomSheet.herbRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ImageActivity)
            adapter = HerbAdapter(ImmutableList.of())
        }
    }

    private fun showEntireImageLabelingResults(herbList: List<Herb>) {
        binding.cardRecyclerView.adapter =
            PreviewCardAdapter(ImmutableList.of()) { showSingleObjectLabelingResults(it) }
        binding.cardRecyclerView.clearOnScrollListeners()
        binding.dotViewContainer.removeAllViews()
        currentSelectedObjectIndex = 0

        detectedBitmapObjectForBottomSheet = null
        binding.bottomSheet.bottomSheetTitle.text =
            resources.getQuantityString(R.plurals.bottom_sheet_title, herbList.size, herbList.size)
        binding.bottomSheet.herbRecyclerView.adapter = HerbAdapter(herbList)
        bottomSheetBehavior?.peekHeight = (binding.inputImageView.parent as View).height / 2
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onObjectsDetected(inputBitmap: Bitmap, objects: List<DetectedObjectInfo>) {
        Log.d(TAG, "Search completed for objects: $objects")
        detectedObjectNum = objects.size
        detectedBitmapObjectMap.clear()
        binding.dotViewContainer.removeAllViews()
        objects.onEach { detectedBitmapObjectMap[it.objectIndex] = DetectedBitmapObject(resources, it) }

        showBottomPromptChip(getString(R.string.static_image_prompt_detected_results))
        binding.cardRecyclerView.adapter =
            PreviewCardAdapter(ImmutableList.copyOf(detectedBitmapObjectMap.values)) {
                showSingleObjectLabelingResults(it)
            }
        binding.cardRecyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    Log.d(TAG, "New card scroll state: $newState")
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        for (i in 0 until recyclerView.childCount) {
                            val childView = recyclerView.getChildAt(i)
                            if (childView.x >= 0) {
                                val cardIndex = recyclerView.getChildAdapterPosition(childView)
                                if (cardIndex != currentSelectedObjectIndex) {
                                    selectNewObject(cardIndex)
                                }
                                break
                            }
                        }
                    }
                }
            })

        for (labeledObject in detectedBitmapObjectMap.values) {
            val dotView = createDotView(inputBitmap, labeledObject)
            dotView.setOnClickListener {
                if (labeledObject.objectIndex == currentSelectedObjectIndex) {
                    showSingleObjectLabelingResults(labeledObject)
                } else {
                    selectNewObject(labeledObject.objectIndex)
                    showSingleObjectLabelingResults(labeledObject)
                    binding.cardRecyclerView.smoothScrollToPosition(labeledObject.objectIndex)
                }
            }

            binding.dotViewContainer.addView(dotView)
            val animatorSet = AnimatorInflater.loadAnimator(this, R.animator.static_image_dot_enter) as AnimatorSet
            animatorSet.setTarget(dotView)
            animatorSet.start()
        }
    }

    private fun showSingleObjectLabelingResults(detectedBitmapObject: DetectedBitmapObject) {
        detectedBitmapObjectForBottomSheet = detectedBitmapObject
        detectedBitmapObject.detectedObject.herbs?.let {
            binding.bottomSheet.bottomSheetTitle.text =
                resources.getQuantityString(R.plurals.bottom_sheet_title, it.size, it.size)
            binding.bottomSheet.herbRecyclerView.adapter = HerbAdapter(it)
            bottomSheetBehavior?.peekHeight = (binding.inputImageView.parent as View).height / 2
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun createDotView(
        inputBitmap: Bitmap,
        detectedBitmapObject: DetectedBitmapObject
    ): StaticObjectDotView {
        val viewCoordinateScale: Float
        val horizontalGap: Float
        val verticalGap: Float
        val inputImageView = binding.inputImageView
        val inputImageViewRatio = inputImageView.width.toFloat() / inputImageView.height
        val inputBitmapRatio = inputBitmap.width.toFloat() / inputBitmap.height
        if (inputBitmapRatio <= inputImageViewRatio) { // Image content fills height
            viewCoordinateScale = inputImageView.height.toFloat() / inputBitmap.height
            horizontalGap = (inputImageView.width - inputBitmap.width * viewCoordinateScale) / 2
            verticalGap = 0f
        } else { // Image content fills width
            viewCoordinateScale = inputImageView.width.toFloat() / inputBitmap.width
            horizontalGap = 0f
            verticalGap = (inputImageView.height - inputBitmap.height * viewCoordinateScale) / 2
        }

        val boundingBox = detectedBitmapObject.boundingBox
        val boxInViewCoordinate = RectF(
            boundingBox.left * viewCoordinateScale + horizontalGap,
            boundingBox.top * viewCoordinateScale + verticalGap,
            boundingBox.right * viewCoordinateScale + horizontalGap,
            boundingBox.bottom * viewCoordinateScale + verticalGap
        )
        val initialSelected = detectedBitmapObject.objectIndex == 0
        val dotView = StaticObjectDotView(this, initialSelected)
        val layoutParams = FrameLayout.LayoutParams(dotViewSize, dotViewSize)
        val dotCenter = PointF(
            (boxInViewCoordinate.right + boxInViewCoordinate.left) / 2,
            (boxInViewCoordinate.bottom + boxInViewCoordinate.top) / 2
        )
        layoutParams.setMargins(
            (dotCenter.x - dotViewSize / 2f).toInt(),
            (dotCenter.y - dotViewSize / 2f).toInt(),
            0,
            0
        )
        dotView.layoutParams = layoutParams
        return dotView
    }

    private fun selectNewObject(objectIndex: Int) {
        val dotViewToDeselect = binding.dotViewContainer.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        dotViewToDeselect.playAnimationWithSelectedState(false)

        currentSelectedObjectIndex = objectIndex

        val selectedDotView = binding.dotViewContainer.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        selectedDotView.playAnimationWithSelectedState(true)
    }

    private fun showBottomPromptChip(message: String) {
        binding.bottomPromptChip.visibility = View.VISIBLE
        binding.bottomPromptChip.text = message
    }

    private fun showSnackBar(message: String, length: Int? = Snackbar.LENGTH_INDEFINITE) {
        snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            length!!,
        )
        snackbar?.setTextMaxLines(10)
        snackbar?.show()
    }

    private class CardItemDecoration constructor(resources: Resources) : RecyclerView.ItemDecoration() {

        private val cardSpacing: Int = resources.getDimensionPixelOffset(R.dimen.preview_card_spacing)

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapterPosition = parent.getChildAdapterPosition(view)
            outRect.left = if (adapterPosition == 0) cardSpacing * 2 else cardSpacing
            val adapter = parent.adapter ?: return
            if (adapterPosition == adapter.itemCount - 1) {
                outRect.right = cardSpacing
            }
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName
    }
}
