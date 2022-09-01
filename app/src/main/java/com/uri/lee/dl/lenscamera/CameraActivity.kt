package com.uri.lee.dl.lenscamera

import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.databinding.ActivityCameraBinding
import com.uri.lee.dl.lenscamera.livecamera.LiveCameraFragment
import com.uri.lee.dl.lenscamera.objectivecamera.ObjectiveCameraFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.objectsModeSwitch.setOnCheckedChangeListener { _, _ -> launchCamera() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // seek bar
                viewModel.state()
                    .mapNotNull { it.confidence }
                    .distinctUntilChanged()
                    .onEach {
                        binding.seekView.seekBar.setProgress((it * 100).toInt(), false)
                        binding.seekView.confidencePercentView.text = "${binding.seekView.seekBar.progress} %"
                        launchCamera()
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
            }
        }
    }

    private fun launchCamera() {
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        } else {
            val confidence = binding.seekView.seekBar.progress.toFloat() / 100
            if (binding.objectsModeSwitch.isChecked) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, ObjectiveCameraFragment.newInstance(confidence))
                    .commitNow()
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, LiveCameraFragment.newInstance(confidence))
                    .commitNow()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val shouldShowSeekBarAndSwitch = !(Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this))
        binding.seekView.root.isVisible = shouldShowSeekBarAndSwitch
        binding.objectsModeSwitch.isVisible = shouldShowSeekBarAndSwitch
        if (supportFragmentManager.findFragmentById(R.id.container) == null) launchCamera()
    }
}
