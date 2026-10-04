package com.uri.lee.dl

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.uri.lee.dl.Utils.displaySpeechRecognizer
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.hometabs.SectionsPagerAdapter
import com.uri.lee.dl.hometabs.TAB_TITLES
import com.uri.lee.dl.search.SPOKEN_TEXT_EXTRA
import com.uri.lee.dl.search.SearchActivity
import com.uri.lee.dl.lenscamera.CameraActivity
import com.uri.lee.dl.lensimage.ImageActivity
import com.uri.lee.dl.lensimages.ImagesActivity
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val mainViewModel: MainViewModel by viewModel()

    @SuppressLint("RestrictedApi")
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(view)

        // Setup tabbed views
        binding.viewPager.adapter = SectionsPagerAdapter(this)
        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, position ->
            tab.text = getString(TAB_TITLES[position])
        }
            .attach()

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDefaultDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.searchView.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.microphoneView.setOnClickListener { displaySpeechRecognizer(this) }
        binding.searchCameraView.setOnClickListener { startActivity(Intent(this, CameraActivity::class.java)) }
        binding.searchMultiImagesView.setOnClickListener { startActivity(Intent(this, ImagesActivity::class.java)) }
        binding.searchSingleImageView.setOnClickListener { startActivity(Intent(this, ImageActivity::class.java)) }

        binding.menuView.setOnClickListener { BottomSheetMenu().show(supportFragmentManager, "ModalBottomSheet") }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.state
                    .map { it.isSignedIn }
                    .distinctUntilChanged()
                    .filter { it == false }
                    .onEach {
                        finishAffinity()
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    }
                    .launchIn(this)

                mainViewModel.state
                    .map { it.status }
                    .distinctUntilChanged()
                    .onEach(::showStatusDialogs)
                    .launchIn(this)
            }
        }
    }

    private fun showStatusDialogs(status: AppStatus) {
        if (status.isSuspended) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.stack_overflow))
                .setCancelable(false)
                .setNeutralButton(getString(android.R.string.ok)) { _, _ ->
                    finish()
                    exitProcess(0)
                }
                .create().show()
        }
        when (status.update) {
            UpdatePolicy.REQUIRED -> AlertDialog.Builder(this)
                .setMessage(getString(R.string.please_update_android))
                .setCancelable(false)
                .setNeutralButton(getString(android.R.string.ok)) { _, _ -> goToPlayStore() }
                .create().show()
            UpdatePolicy.RECOMMENDED -> AlertDialog.Builder(this)
                .setMessage(getString(R.string.please_update_android))
                .setCancelable(true)
                .setPositiveButton(getString(android.R.string.ok)) { _, _ -> goToPlayStore() }
                .setNeutralButton(getString(android.R.string.cancel)) { _, _ -> }
                .create().show()
            UpdatePolicy.NONE -> Unit
        }
    }

    override fun onStart() {
        super.onStart()
        Utils.requestNotificationPermission(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                Utils.SPEECH_REQUEST_CODE -> {
                    val spokenText: String = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)!![0]
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra(SPOKEN_TEXT_EXTRA, spokenText)
                    startActivity(intent)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

