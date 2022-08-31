package com.uri.lee.dl.herbdetails

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageUploadBinding
import kotlinx.coroutines.launch
import timber.log.Timber

class ImageUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageUploadBinding

    private lateinit var imageUploadAdapter: ImageUploadAdapter

    private val viewModel: ImageUploadViewModel by viewModels()

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { viewModel.addImageUris(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageUploadBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        intent.getStringExtra(HERB_ID)?.let { viewModel.setHerbId(it) }
        imageUploadAdapter = ImageUploadAdapter {

        }
        val staggeredGridLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        binding.recyclerView.layoutManager = staggeredGridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter
        val photoList: MutableList<Uri> = mutableListOf<Uri>()
        Timber.d("triennn $photoList")
        imageUploadAdapter.submitList(photoList)

        binding.addImagesBtn.setOnClickListener { resultLauncher.launch("image/*") }
        binding.pickPhotosView.setOnClickListener { resultLauncher.launch("image/*") }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

            }
        }
    }


    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }
}
