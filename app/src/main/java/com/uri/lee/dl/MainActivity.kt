package com.uri.lee.dl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.uri.lee.dl.Utils.displaySpeechRecognizer
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.hometabs.SectionsPagerAdapter
import com.uri.lee.dl.instantsearch.SPOKEN_TEXT_EXTRA
import com.uri.lee.dl.instantsearch.SearchActivity
import com.uri.lee.dl.lenscamera.CameraActivity
import com.uri.lee.dl.lensimage.ImageActivity
import com.uri.lee.dl.lensimages.ImagesActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val userViewModel: UserViewModel by viewModels()

    private val configViewModel: ConfigViewModel by viewModels()

    private val authStateListener = AuthStateListener(this)

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Setup tabbed views
        binding.viewPager.adapter = SectionsPagerAdapter(this)
        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, position ->
            tab.text = getString(TAB_TITLES[position])
        }
            .attach()

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDefaultDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false);

        binding.searchView.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.menuView.setOnClickListener {
            val bottomSheet = BottomSheetMenu()
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        binding.microphoneView.setOnClickListener { displaySpeechRecognizer(this) }

        binding.searchCameraView.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.searchMultiImagesView.setOnClickListener {
            startActivity(Intent(this, ImagesActivity::class.java))
        }

        binding.searchSingleImageView.setOnClickListener {
            startActivity(Intent(this, ImageActivity::class.java))
        }

        downloadModel()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configViewModel.state()
                    .mapNotNull { it.mobile }
                    .distinctUntilChanged()
                    .onEach {
                        if (it.stackOverflow) {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(getString(R.string.stack_overflow))
                                .setCancelable(false)
                                .setNeutralButton(getString(android.R.string.ok)) { _, _ ->
                                    this@MainActivity.finish()
                                    exitProcess(0)
                                }
                                .create()
                                .show()
                        }
                        if (it.mustUpdateAndroid) {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(getString(R.string.please_update_android))
                                .setCancelable(false)
                                .setNeutralButton(getString(android.R.string.ok)) { _, _ -> goToPlayStore() }
                                .create().show()
                        }
                        if (it.shouldUpdateAndroid) {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(getString(R.string.please_update_android))
                                .setCancelable(true)
                                .setPositiveButton(getString(android.R.string.ok)) { _, _ -> goToPlayStore() }
                                .setNeutralButton(getString(android.R.string.cancel)) { _, _ -> }
                                .create().show()
                        }
                        if (it.bannedUsers.contains(authUI.auth.uid)) {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(getString(R.string.you_have_been_banned))
                                .setCancelable(false)
                                .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                                    this@MainActivity.finish()
                                    exitProcess(0)
                                }
                                .setNegativeButton(getString(R.string.contact_us)) { _, _ ->
                                    sendEmail(subject = "")
                                }
                                .create().show()
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        authUI.auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        authUI.auth.removeAuthStateListener(authStateListener)
    }

    private fun downloadModel() {
        val remoteModel = CustomRemoteModel
            .Builder(FirebaseModelSource.Builder(REMOTE_TFLITE_MODEL_NAME).build())
            .build()
        val downloadConditions = DownloadConditions.Builder().requireWifi().build()
        RemoteModelManager.getInstance().download(remoteModel, downloadConditions)
            .addOnSuccessListener { Timber.d("Model download completed") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
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
