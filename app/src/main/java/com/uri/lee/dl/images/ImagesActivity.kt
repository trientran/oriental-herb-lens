package com.uri.lee.dl.images

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.Utils.getImagePickerIntent
import com.uri.lee.dl.databinding.ActivityImagesBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class ImagesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImagesBinding

    // Contains the recognition result. Since  it is a viewModel, it will survive screen rotations
    private val viewModel: ImagesViewModel by viewModels()
    private lateinit var viewAdapter: ImagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagesBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        binding.closeButton.setOnClickListener { finish() }
        binding.addImagesBtn.setOnClickListener { addImages() }
        binding.pickImagesView.setOnClickListener { addImages() }
        binding.clearBtn.setOnClickListener { onClearBtnClick() }

        // Initialising the RecyclerView and its linked Adapter
        viewAdapter = ImagesAdapter(this) {
            val bottomSheet = ImagesBottomSheetDialog(it, this)
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }
        binding.recyclerView.adapter = viewAdapter

        // initialize an instance of linear layout manager
        val layoutOrientation =
            (binding.recyclerView.layoutManager as LinearLayoutManager).orientation
        //val dividerItemDecoration = DividerItemDecoration(this, layoutOrientation)
        //binding.recyclerView.addItemDecoration(dividerItemDecoration)

        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                viewModel.recognitionList.collect {
                    binding.pickImagesView.isVisible = it.isEmpty()
                    viewAdapter.submitList(it)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    private fun addImages() {
        // Create intent for picking a photo from the gallery
        //  val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        resultLauncher.launch(getImagePickerIntent(allowMultipleImages = true))
    }

    private fun onClearBtnClick() {
        viewModel.clearAllData()
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        Log.d("trien111", clipData.getItemAt(i).uri.toString())
                    }
                    try {
                        viewModel.inferImages(this, clipData)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Timber.e(e.message ?: "Some error")
                        Toast.makeText(
                            this,
                            getString(R.string.something_went_wrong_please_try_again_or_contact_us),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
}
