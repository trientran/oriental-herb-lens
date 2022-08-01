package com.uri.lee.dl.images

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.databinding.ImagesBottomSheetDialogBinding
import com.uri.lee.dl.images.ImagesState.Recognition
import com.uri.lee.dl.labeling.HerbAdapter

class ImagesBottomSheetDialog(private val recognition: Recognition) : BottomSheetDialogFragment() {

    private lateinit var binding: ImagesBottomSheetDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = ImagesBottomSheetDialogBinding.inflate(layoutInflater)
        val view = binding.root

        binding.herbRecyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(view.context)
            adapter = HerbAdapter(recognition.herbs)
            ViewCompat.setNestedScrollingEnabled(this, false);
        }
        Glide.with(this).load(recognition.fileUri).into(binding.localImage)

        return view
    }
}