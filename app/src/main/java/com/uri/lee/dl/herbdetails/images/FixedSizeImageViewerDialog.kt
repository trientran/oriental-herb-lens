package com.uri.lee.dl.herbdetails.images

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.databinding.FixedSizeImageViewerBinding

class FixedSizeImageViewerDialog(private val uri: Uri) : BottomSheetDialogFragment() {

    private lateinit var binding: FixedSizeImageViewerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = FixedSizeImageViewerBinding.inflate(layoutInflater)
        val view = binding.root
        Glide.with(this).load(uri).into(binding.imageView)
        return view
    }
}
