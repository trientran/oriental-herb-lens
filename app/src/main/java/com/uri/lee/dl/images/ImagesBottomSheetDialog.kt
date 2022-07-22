package com.uri.lee.dl.images

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ImagesBottomSheetDialogBinding
import com.uri.lee.dl.labeling.Herb

class ImagesBottomSheetDialog(private val herb: Herb, private val activity: Activity) : BottomSheetDialogFragment() {

    private lateinit var binding: ImagesBottomSheetDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = ImagesBottomSheetDialogBinding.inflate(layoutInflater)
        val view = binding.root
        Glide.with(this).load(herb.imageFileUri).into(binding.imageView)
        binding.idView.text = herb.id
        binding.sciNameView.text = herb.sciName
        binding.viNameView.text = herb.viName
        binding.searchWithGoogleUsingVietnameseView.isVisible = herb.viName != null
        binding.searchWithGoogleUsingScientificView.isVisible = herb.sciName != null
        herb.viName?.apply {
            binding.searchWithGoogleUsingVietnameseView.setOnClickListener {
                Utils.openUrlWithDefaultBrowser(activity, this)
            }
        }
        herb.sciName?.apply {
            binding.searchWithGoogleUsingScientificView.setOnClickListener {
                Utils.openUrlWithDefaultBrowser(activity, this)
            }
        }
        return view
    }
}