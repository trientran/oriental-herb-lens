package com.uri.lee.dl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.uri.lee.dl.Utils.displaySpeechRecognizer
import com.uri.lee.dl.camera.livecamera.LiveCameraActivity
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.hometabs.SectionsPagerAdapter
import com.uri.lee.dl.image.ImageActivity
import com.uri.lee.dl.images.ImagesActivity
import com.uri.lee.dl.instantsearch.SPOKEN_TEXT_EXTRA
import com.uri.lee.dl.instantsearch.SearchActivity
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val authUI = AuthUI.getInstance()
    private val signInLauncher =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { onSignInResult(it) }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // Setup tabbed views
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDefaultDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false);

        binding.searchView.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.menuView.setOnClickListener {
            val bottomSheet = BottomSheetDialog()
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        binding.microphoneView.setOnClickListener { displaySpeechRecognizer(this) }

        binding.searchCameraView.setOnClickListener {
            startActivity(Intent(this, LiveCameraActivity::class.java))
        }

        binding.searchMultiImagesView.setOnClickListener {
            startActivity(Intent(this, ImagesActivity::class.java))
        }

        binding.searchSingleImageView.setOnClickListener {
            startActivity(Intent(this, ImageActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
//        if (authUI.auth.currentUser == null) {
//            val signInIntent = authUI
//                .createSignInIntentBuilder()
//                .setAvailableProviders(arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build()))
//                .setLogo(R.drawable.ic_launcher_round)
//                .setTheme(R.style.AppTheme)
//                .build()
//            signInLauncher.launch(signInIntent)}

    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(ioDispatcher) {
            // Specify the name you assigned in the Firebase console.
            val remoteModel = CustomRemoteModel
                .Builder(FirebaseModelSource.Builder(REMOTE_TFLITE_MODEL_NAME).build())
                .build()

            val downloadConditions = DownloadConditions.Builder().requireWifi().build()

            RemoteModelManager.getInstance().download(remoteModel, downloadConditions)
                .addOnSuccessListener {
                    Timber.d("Model download completed")
                }
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            val user = authUI.auth.currentUser
            require(user != null)
            // todo write to FireStore user
        }
    }

    private fun signOut() {
        authUI
            .signOut(this)
            .addOnCompleteListener {
                // ...
            }
    }

    private fun delete() {
        authUI
            .delete(this)
            .addOnCompleteListener {
                // ...
            }
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
