package com.uri.lee.dl.lenscamera.livecamera

import android.os.Bundle
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.uri.lee.dl.databinding.FragmentLiveCameraBinding
import timber.log.Timber
import java.util.concurrent.Executors

class LiveCameraFragment(private val confidence: Float) : Fragment() {

    private lateinit var preview: Preview // Preview use case, fast, responsive view of the camera
    private lateinit var imageAnalyzer: ImageAnalysis // Analysis use case, for running ML code
    private lateinit var camera: Camera

    private val liveCameraViewModel: LiveCameraViewModel by viewModel()

    private lateinit var binding: FragmentLiveCameraBinding

    companion object {
        fun newInstance(confidence: Float) =
            LiveCameraFragment(confidence) // keep this so that we can pass args values later if needed
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLiveCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialising the resultRecyclerView and its linked viewAdaptor
        val viewAdapter = CameraAdapter(requireContext())
        binding.recyclerView.adapter = viewAdapter

        // Disable recycler view animation to reduce flickering, otherwise items can move, fade in
        // and out as the list change
        binding.recyclerView.itemAnimator = null

        binding.closeButton.setOnClickListener { requireActivity().finish() }

        startCamera(confidence)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                liveCameraViewModel.state.collect { viewAdapter.submitList(it.herbs) }
            }
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Select camera, back is the default. If it is not available, choose front camera
            val cameraSelector =
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            preview = Preview.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                // CameraX picks the closest supported resolution
                .setTargetResolution(Size(600, 600))
                // Drop stale frames rather than queue them
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), liveCameraViewModel.analyzer(confidence)) }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera - try to bind everything at once and CameraX will find
                // the best combination.
                camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

                binding.flashButton.apply {
                    isVisible = camera.cameraInfo.hasFlashUnit()
                    setOnClickListener {
                        if (it.isSelected) {
                            it.isSelected = false
                            camera.cameraControl.enableTorch(false)
                        } else {
                            it.isSelected = true
                            camera.cameraControl.enableTorch(true)
                        }
                    }
                }

                // Attach the preview to preview view, aka View Finder
                preview.setSurfaceProvider(binding.cameraView.surfaceProvider)
            } catch (e: Exception) {
                Timber.e(e.message ?: "Some error")
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }
}
