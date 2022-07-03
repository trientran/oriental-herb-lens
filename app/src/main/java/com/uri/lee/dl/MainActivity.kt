package com.uri.lee.dl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewpager.widget.ViewPager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.tabs.TabLayout
import com.uri.lee.dl.Utils.displaySpeechRecognizer
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.instantsearch.SPOKEN_TEXT_EXTRA
import com.uri.lee.dl.instantsearch.SearchActivity
import com.uri.lee.dl.ui.main.SectionsPagerAdapter

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
        binding.searchHint.setOnClickListener { binding.searchView.performClick() }

        binding.menuView.setOnClickListener {
            val bottomSheet = BottomSheetDialog()
            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
        }

        binding.microphoneView.setOnClickListener { displaySpeechRecognizer(this) }

        binding.searchCameraView.setOnClickListener {
            startActivity(Intent(this, LiveObjectDetectionActivity::class.java))
        }

        binding.searchMultiImagesView.setOnClickListener {

        }

        binding.searchSingleImageView.setOnClickListener { Utils.openImagePicker(this) }
    }

    override fun onStart() {
        super.onStart()
        if (authUI.auth.currentUser == null) {
            val signInIntent = authUI
                .createSignInIntentBuilder()
                .setAvailableProviders(arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build()))
                .setLogo(R.drawable.ic_launcher_round)
                .setTheme(R.style.AppTheme)
                .build()
            signInLauncher.launch(signInIntent)

            if (!Utils.allPermissionsGranted(this)) {
                Utils.requestRuntimePermissions(this)
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
                Utils.REQUEST_CODE_PHOTO_LIBRARY -> {
                    val intent = Intent(this, StaticObjectDetectionActivity::class.java)
                    intent.data = data.data
                    startActivity(intent)
                }
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
