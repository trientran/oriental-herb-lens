package com.uri.lee.dl.upload

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.davemorrissey.labs.subscaleview.ImageSource
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.databinding.FullSizeImageViewerBinding
import com.uri.lee.dl.labeling.HerbAdapter

class FullSizeImageViewerDialog(private val uri: Uri) : BottomSheetDialogFragment() {

    private lateinit var binding: FullSizeImageViewerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = FullSizeImageViewerBinding.inflate(layoutInflater)
        val view = binding.root

        binding.imageView.setImage(ImageSource.uri(uri))

        return view
    }
}
