package com.uri.lee.dl.camera.livecamera

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityLiveCameraBinding
import timber.log.Timber
import java.util.concurrent.Executors

class LiveCameraActivity : AppCompatActivity() {

    private lateinit var preview: Preview // Preview use case, fast, responsive view of the camera
    private lateinit var imageAnalyzer: ImageAnalysis // Analysis use case, for running ML code
    private lateinit var camera: Camera

    private val cameraViewModel: CameraViewModel by viewModels()

    private lateinit var binding: ActivityLiveCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // Initialising the resultRecyclerView and its linked viewAdaptor
        val viewAdapter = CameraAdapter(this)
        binding.recyclerView.adapter = viewAdapter

        // Disable recycler view animation to reduce flickering, otherwise items can move, fade in
        // and out as the list change
        binding.recyclerView.itemAnimator = null

        binding.closeButton.setOnClickListener { finish() }

        binding.seekView.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                binding.seekView.confidencePercentView.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                startCamera(seekBar.progress.toFloat() / 100)
            }
        })

        // Attach an observer on the LiveData field of recognitionList
        // This will notify the recycler view to update every time when a new list is set on the
        // LiveData field of recognitionList.
        cameraViewModel.recognitionList.observe(this) {
            viewAdapter.submitList(it)
        }

        binding.seekView.seekBar.progress = 50
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        } else {
            startCamera(binding.seekView.seekBar.progress.toFloat() / 100)
        }
    }

    /**
     * Start the Camera which involves:
     *
     * 1. Initialising the preview use case
     * 2. Initialising the image analyser use case
     * 3. Attach both to the lifecycle of this activity
     * 4. Pipe the output of the preview object to the PreviewView on the screen
     */
    private fun startCamera(confidence: Float) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Select camera, back is the default. If it is not available, choose front camera
            val cameraSelector =
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            preview = Preview.Builder().build()
            imageAnalyzer =
                cameraViewModel.analyzeImage(Executors.newSingleThreadExecutor(), confidence)

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera - try to bind everything at once and CameraX will find
                // the best combination.
                camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

                // Attach the preview to preview view, aka View Finder
                preview.setSurfaceProvider(binding.cameraView.surfaceProvider)
            } catch (e: Exception) {
                Timber.e(e.message ?: "Some error")
            }

        }, ContextCompat.getMainExecutor(this))
    }
}
