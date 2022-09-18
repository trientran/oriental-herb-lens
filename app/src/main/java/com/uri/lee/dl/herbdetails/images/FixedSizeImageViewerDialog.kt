package com.uri.lee.dl.herbdetails.images

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils.openUrlWithDefaultBrowser
import com.uri.lee.dl.databinding.FixedSizeImageViewerBinding

class FixedSizeImageViewerDialog(
    private val uriPair: Pair<Uri, String>,
    private val onDeleteImage: (ImageDeleteReason) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: FixedSizeImageViewerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = FixedSizeImageViewerBinding.inflate(layoutInflater)
        val view = binding.root
        Glide.with(this).load(uriPair.first).into(binding.imageView)
        binding.uploadByView.text = getString(R.string.uploaded_by, uriPair.second)
        binding.imageView.setOnClickListener { view.context.openUrlWithDefaultBrowser(uri = uriPair.first) }
        binding.deleteBtn.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setMessage(getString(R.string.please_let_us_know_why_delete_this_image))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.faulty_image)) { _, _ ->
                    onDeleteImage.invoke(ImageDeleteReason.FaultyImage)
                }
                .setNegativeButton(getString(R.string.duplicate_image)) { _, _ ->
                    onDeleteImage.invoke(ImageDeleteReason.DuplicatedImage)
                }
                .setNeutralButton(getString(R.string.cancel)) { _, _ -> dismiss() }
                .create().show()
            dismiss()
        }
        return view
    }
}

interface ImageDeleteReason {
    object FaultyImage : ImageDeleteReason
    object DuplicatedImage : ImageDeleteReason
    object Duplicate2dImage : ImageDeleteReason
}
