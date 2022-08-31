package com.uri.lee.dl.images

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityImagesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ImagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagesBinding

    // Contains the recognition result. Since  it is a viewModel, it will survive screen rotations
    private val viewModel: ImagesViewModel by viewModels()
    private lateinit var viewAdapter: ImagesAdapter
    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagesBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Initialising the RecyclerView and its linked Adapter
        viewAdapter = ImagesAdapter {
            val bottomSheet = ImagesBottomSheetDialog(it)
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }
        binding.recyclerView.adapter = viewAdapter

        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                this.setUpComponents()
            }
        }
    }

    private val browseImagesTimer = object : CountDownTimer(5000, 5000) {
        override fun onTick(millisUntilFinished: Long) {
            binding.pickImagesView.isEnabled = false
            binding.addImagesBtn.isEnabled = false
            resultLauncher.launch("image/*")
        }

        override fun onFinish() {
            binding.pickImagesView.isEnabled = true
            binding.addImagesBtn.isEnabled = true
        }
    }

    private fun CoroutineScope.setUpComponents() {
        binding.closeButton.setOnClickListener { finish() }
        binding.addImagesBtn.setOnClickListener { browseImagesTimer.start() }
        binding.pickImagesView.setOnClickListener { browseImagesTimer.start() }
        binding.clearBtn.setOnClickListener { viewModel.clearAllData() }

        viewModel.state()
            .map { it.recognitionList }
            .onEach {
                binding.clearBtn.isVisible = it.isNotEmpty()
                binding.pickImagesView.isVisible = it.isEmpty()
                binding.seekView.root.isVisible = it.isNotEmpty()
                viewAdapter.submitList(it)
            }
            .launchIn(this)

        // seek bar
        viewModel.state()
            .mapNotNull { it.confidence }
            .take(1)
            .onEach {
                binding.seekView.seekBar.setProgress((it * 100).toInt(), false)
                binding.seekView.confidencePercentView.text = "${binding.seekView.seekBar.progress} %"
            }
            .launchIn(this)
        binding.seekView.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                binding.seekView.confidencePercentView.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                viewModel.setConfidence((seekBar.progress.toFloat() / 100))
            }
        })

        viewModel.state()
            .map { it.event }
            .onEach {
                when (it) {
                    is ImagesState.Event.BitmapError -> showSnackBar(getString(R.string.failed_to_load_file_please_try_again))
                    is ImagesState.Event.LabelingError -> showSnackBar(getString(R.string.failed_to_label_one_or_some_of_the_images))
                    is ImagesState.Event.DataStoreError -> showSnackBar(getString(R.string.failed_to_load_local_data_store))
                    null -> snackbar?.dismiss()
                    is ImagesState.Event.Other -> showSnackBar(getString(R.string.something_went_wrong_please_try_again_or_contact_us))
                }
            }
            .launchIn(this)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { viewModel.addImageUris(it) }

    private fun showSnackBar(message: String, length: Int? = Snackbar.LENGTH_INDEFINITE) {
        snackbar = Snackbar.make(findViewById(android.R.id.content), message, length!!)
        snackbar?.setTextMaxLines(10)
        snackbar?.show()
    }
}
