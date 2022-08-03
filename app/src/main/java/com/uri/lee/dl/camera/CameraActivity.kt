package com.uri.lee.dl.camera

import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.uri.lee.dl.R
import com.uri.lee.dl.Utils
import com.uri.lee.dl.camera.livecamera.LiveCameraFragment
import com.uri.lee.dl.camera.objectivecamera.ObjectiveCameraFragment
import com.uri.lee.dl.databinding.ActivityCameraBinding
import com.uri.lee.dl.settings.SettingsActivity
import timber.log.Timber

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.actionBar.closeButton.setOnClickListener { finish() }
        binding.actionBar.settingsButton.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.actionBar.flashButton.setOnClickListener {
            val camManager = getSystemService(CAMERA_SERVICE) as CameraManager
            if (it.isSelected) {
                it.isSelected = false
                try {
                    camManager.setTorchMode(camManager.cameraIdList[0], false)
                } catch (e: CameraAccessException) {
                    Timber.e(e.message)
                }
            } else {
                it.isSelected = true
                try {
                    camManager.setTorchMode(camManager.cameraIdList[0], true)
                } catch (e: CameraAccessException) {
                    Timber.e(e.message)
                }
            }
        }
        binding.actionBar.objectsModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val confidence = binding.actionBar.seekView.seekBar.progress.toFloat()
            if (isChecked) {
                Timber.d("trien checked")
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, ObjectiveCameraFragment.newInstance(confidence))
                    .commitNow()
            } else {
                Timber.d("trien not checked")

                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, LiveCameraFragment.newInstance(confidence))
                    .commitNow()
            }
        }
        binding.actionBar.seekView.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                binding.actionBar.seekView.confidencePercentView.text = "$progress %"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val isObjectMode = binding.actionBar.objectsModeSwitch.isChecked
                val confidence = seekBar.progress.toFloat() / 100
                if (isObjectMode) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, ObjectiveCameraFragment.newInstance(confidence))
                        .commitNow()
                } else {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.container, LiveCameraFragment.newInstance(confidence))
                        .commitNow()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT <= 28 && !Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        } else {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.container,
                    ObjectiveCameraFragment.newInstance(binding.actionBar.seekView.seekBar.progress.toFloat() / 100)
                )
                .commitNow()
        }
    }
}
