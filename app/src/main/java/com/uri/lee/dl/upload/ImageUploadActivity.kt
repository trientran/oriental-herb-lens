package com.uri.lee.dl.upload

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImageUploadBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
// todo open bottomsheetdialog showing full image
        }
        val gridLayoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = imageUploadAdapter
        val photoList: MutableList<Uri> = mutableListOf<Uri>()
        Timber.d("triennn $photoList")
        imageUploadAdapter.submitList(photoList)

        binding.addImagesBtn.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }
        binding.pickPhotosView.setOnClickListener {
            it.isEnabled = false
            resultLauncher.launch("image/*")
        }

        binding.uploadBtn.setOnClickListener {
            GlobalScope.launch {
                val quotesApi = RetrofitHelper.getInstance().create(QuotesApi::class.java)
                // launching a new coroutine
                val result = quotesApi.getQuotes()
                if (result != null)
                // Checking the results
                    Log.d("ayush: ", result.body().toString())
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state()
                    .map { it.imageUris }
                    .distinctUntilChanged()
                    .onEach { imageUploadAdapter.submitList(it) }
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
