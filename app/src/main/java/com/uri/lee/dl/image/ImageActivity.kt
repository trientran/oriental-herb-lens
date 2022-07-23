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
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.common.collect.ImmutableList
import com.google.mlkit.vision.objects.DetectedObject
import com.uri.lee.dl.*
import com.uri.lee.dl.labeling.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.*

/** Demonstrates the object detection and visual search workflow using static image.  */
class ImageActivity : AppCompatActivity(), View.OnClickListener {

    private val detectedObjectMap = TreeMap<Int, com.uri.lee.dl.labeling.DetectedObject>()
    private val viewModel: ImageViewModel by viewModels { ExtraParamsViewModelFactory(application) }
    private var bottomPromptChip: Chip? = null
    private var snackbar: Snackbar? = null
    private var entireImageSwitchCompat: SwitchCompat? = null
    private var inputImageView: ImageView? = null
    private var previewCardCarousel: RecyclerView? = null
    private var dotViewContainer: ViewGroup? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var bottomSheetTitleView: TextView? = null
    private var productRecyclerView: RecyclerView? = null

    private var detectedObjectForBottomSheet: com.uri.lee.dl.labeling.DetectedObject? = null
    private var dotViewSize: Int = 0
    private var detectedObjectNum = 0
    private var currentSelectedObjectIndex = 0

    private var searchEngine: LabelImage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchEngine = LabelImage(applicationContext)

        setContentView(R.layout.activity_image)

        bottomPromptChip = findViewById(R.id.bottom_prompt_chip)
        entireImageSwitchCompat = findViewById(R.id.entire_image_mode_switch)
        inputImageView = findViewById(R.id.input_image_view)

        previewCardCarousel = findViewById<RecyclerView>(R.id.card_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ImageActivity, RecyclerView.HORIZONTAL, false)
            addItemDecoration(
                CardItemDecoration(
                    resources
                )
            )
        }

        dotViewContainer = findViewById(R.id.dot_view_container)
        dotViewSize = resources.getDimensionPixelOffset(R.dimen.static_image_dot_view_size)

        setUpBottomSheet()

        findViewById<View>(R.id.close_button).setOnClickListener(this)
        findViewById<View>(R.id.pickImageView).setOnClickListener(this)
        findViewById<View>(R.id.photo_library_button).setOnClickListener(this)

        viewModel.isLoading.observe(this) {
            findViewById<View>(R.id.loading_view).isVisible = it
        }
        viewModel.error.observe(this) {
            when (it) {
                is HerbEvent.BitmapError -> showSnackBar(getString(R.string.failed_to_load_file_please_try_again))
                is HerbEvent.LabelingError -> showSnackBar(getString(R.string.failed_to_label_please_turn_on_entire_image_mode))
                is HerbEvent.ObjectDetectionError -> showSnackBar(getString(R.string.static_image_detected_no_results_continue_to_label))
                HerbEvent.NoHerbObjects -> showSnackBar(getString(R.string.no_herb_objects_result_please_try_entire_image_mode))
                null -> snackbar?.dismiss()
            }
        }
        val imageUri = viewModel.imageUri
        imageUri.observe(this) { uri ->
            Glide.with(this@ImageActivity).load(uri).into(inputImageView as ImageView)
            findViewById<View>(R.id.pickImageView).isVisible = false
            if (viewModel.isEntireImageMode.value == true) {
                viewModel.inferEntireImageLabels(
                    context = this@ImageActivity,
                    uri = uri,
                    confidence = 0.5f,
                ) { showEntireImageLabelingResults(it) }
            } else {
                detectObjects(uri)
            }
        }

        entireImageSwitchCompat?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEntireImageMode(isChecked, this)
            imageUri.value?.let { uri ->
                if (isChecked) {
                    viewModel.inferEntireImageLabels(
                        context = this@ImageActivity,
                        uri = uri,
                        confidence = 0.5f,
                    ) { showEntireImageLabelingResults(it) }
                } else {
                    detectObjects(uri)
                }
            }
        }
        dataStore.data
            .map { settings -> settings[IS_ENTIRE_IMAGE_MODE_SINGLE_IMAGE] ?: false }
            .distinctUntilChanged()
            .onEach {
                entireImageSwitchCompat?.isChecked = it
                previewCardCarousel?.isVisible = !it
                dotViewContainer?.isVisible = !it
                if (it) bottomPromptChip?.isVisible = false
            }
            .launchIn(lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY && resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { viewModel.setImageUri(it) }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
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
            R.id.close_button -> finish()
            R.id.photo_library_button, R.id.pickImageView -> Utils.openImagePicker(this)
            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.state =
                BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun showObjectsLabelingResults(detectedObject: com.uri.lee.dl.labeling.DetectedObject) {
        detectedObjectForBottomSheet = detectedObject
        val productList = detectedObject.herbList
        bottomSheetTitleView?.text = resources
            .getQuantityString(
                R.plurals.bottom_sheet_title, productList.size, productList.size
            )
        productRecyclerView?.adapter = HerbAdapter(this, productList)
        bottomSheetBehavior?.peekHeight = (inputImageView?.parent as View).height / 2
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun showEntireImageLabelingResults(herbList: List<Herb>) {
        detectedObjectForBottomSheet = null
        bottomSheetTitleView?.text =
            resources.getQuantityString(R.plurals.bottom_sheet_title, herbList.size, herbList.size)
        productRecyclerView?.adapter = HerbAdapter(this, herbList)
        bottomSheetBehavior?.peekHeight = (inputImageView?.parent as View).height / 2
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById<View>(R.id.bottom_sheet)).apply {
            setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        Log.d(TAG, "Bottom sheet new state: $newState")
                        bottomSheetScrimView?.visibility =
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        if (java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val collapsedStateHeight = bottomSheetBehavior!!.peekHeight.coerceAtMost(bottomSheet.height)
                        val searchedObjectForBottomSheet = detectedObjectForBottomSheet
                            ?: return
                        bottomSheetScrimView?.updateWithThumbnailTranslate(
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

        bottomSheetScrimView = findViewById<BottomSheetScrimView>(R.id.bottom_sheet_scrim_view).apply {
            setOnClickListener(this@ImageActivity)
        }

        bottomSheetTitleView = findViewById(R.id.bottom_sheet_title)
        productRecyclerView = findViewById<RecyclerView>(R.id.product_recycler_view)?.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@ImageActivity)
            adapter = HerbAdapter(this@ImageActivity, ImmutableList.of())
        }
    }

    private fun detectObjects(imageUri: Uri) {
        bottomPromptChip?.visibility = View.GONE
        previewCardCarousel?.adapter = PreviewCardAdapter(ImmutableList.of()) { showObjectsLabelingResults(it) }
        previewCardCarousel?.clearOnScrollListeners()
        dotViewContainer?.removeAllViews()
        currentSelectedObjectIndex = 0
        viewModel.detectObject(this, imageUri) { entireBitmap, list -> onObjectsDetected(entireBitmap, list) }
    }

    private fun onObjectsDetected(entireBitmap: Bitmap, objects: List<DetectedObject>) {
        detectedObjectNum = objects.size
        Log.d(TAG, "Detected objects num: $detectedObjectNum")
        detectedObjectMap.clear()
        var herbObjectsCount = 0
        for (i in objects.indices) {
            val detectedGeneralObjectInfo = DetectedObjectInfo(objects[i], i, BitmapInputInfo(entireBitmap))
            viewModel.inferObjectsLabels(bitmap = detectedGeneralObjectInfo.getBitmap()) {
                val detectedHerbObjectInfo =
                    DetectedObjectInfo(objects[herbObjectsCount], herbObjectsCount, BitmapInputInfo(entireBitmap))
                onObjectLabellingCompleted(entireBitmap, detectedHerbObjectInfo, it)
                herbObjectsCount++
            }
        }
    }

    private fun onObjectLabellingCompleted(
        inputBitmap: Bitmap,
        detectedObjectInfo: DetectedObjectInfo,
        recognitionList: List<Herb>
    ) {
        Log.d(TAG, "Search completed for object index: ${detectedObjectInfo.objectIndex}")
        detectedObjectMap[detectedObjectInfo.objectIndex] =
            DetectedObject(resources, detectedObjectInfo, recognitionList)

        showBottomPromptChip(getString(R.string.static_image_prompt_detected_results))
        previewCardCarousel?.adapter =
            PreviewCardAdapter(ImmutableList.copyOf(detectedObjectMap.values)) { showObjectsLabelingResults(it) }
        previewCardCarousel?.addOnScrollListener(
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

        for (labeledObject in detectedObjectMap.values) {
            val dotView = createDotView(inputBitmap, labeledObject)
            dotView.setOnClickListener {
                if (labeledObject.objectIndex == currentSelectedObjectIndex) {
                    showObjectsLabelingResults(labeledObject)
                } else {
                    selectNewObject(labeledObject.objectIndex)
                    showObjectsLabelingResults(labeledObject)
                    previewCardCarousel!!.smoothScrollToPosition(labeledObject.objectIndex)
                }
            }

            dotViewContainer?.addView(dotView)
            val animatorSet = AnimatorInflater.loadAnimator(this, R.animator.static_image_dot_enter) as AnimatorSet
            animatorSet.setTarget(dotView)
            animatorSet.start()
        }
    }

    private fun createDotView(
        inputBitmap: Bitmap,
        detectedObject: com.uri.lee.dl.labeling.DetectedObject
    ): StaticObjectDotView {
        val viewCoordinateScale: Float
        val horizontalGap: Float
        val verticalGap: Float
        val inputImageView = inputImageView ?: throw NullPointerException()
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

        val boundingBox = detectedObject.boundingBox
        val boxInViewCoordinate = RectF(
            boundingBox.left * viewCoordinateScale + horizontalGap,
            boundingBox.top * viewCoordinateScale + verticalGap,
            boundingBox.right * viewCoordinateScale + horizontalGap,
            boundingBox.bottom * viewCoordinateScale + verticalGap
        )
        val initialSelected = detectedObject.objectIndex == 0
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
        val dotViewToDeselect = dotViewContainer!!.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        dotViewToDeselect.playAnimationWithSelectedState(false)

        currentSelectedObjectIndex = objectIndex

        val selectedDotView = dotViewContainer!!.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        selectedDotView.playAnimationWithSelectedState(true)
    }

    private fun showBottomPromptChip(message: String) {
        bottomPromptChip?.visibility = View.VISIBLE
        bottomPromptChip?.text = message
    }

    private fun showSnackBar(message: String) {
        snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_INDEFINITE
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
