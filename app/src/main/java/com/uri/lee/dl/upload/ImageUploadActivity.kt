package com.uri.lee.dl.upload

import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageUploadBinding
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ImageUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageUploadBinding

    private lateinit var imageUploadAdapter: ImageUploadAdapter

    private val viewModel: ImageUploadViewModel by viewModels()

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { list -> viewModel.addImageUris(list) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageUploadBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        intent.getStringExtra(HERB_ID)?.let { viewModel.setHerbId(it) }
        imageUploadAdapter = ImageUploadAdapter {
// todo open bottomsheetdialog showing full image
        }
        val gridLayoutManager = StaggeredGridLayoutManager(2, GridLayoutManager.VERTICAL)
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter

        binding.addImagesBtn.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }
        binding.pickPhotosView.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }
        binding.uploadBtn.setOnClickListener { viewModel.uploadSequentially() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state()
                    .map { it.imageUris }
                    .distinctUntilChanged()
                    .onEach { uriList -> imageUploadAdapter.submitList(uriList) }
                    .launchIn(this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.addImagesBtn.isEnabled = true
        binding.pickPhotosView.isEnabled = true
    }
}
