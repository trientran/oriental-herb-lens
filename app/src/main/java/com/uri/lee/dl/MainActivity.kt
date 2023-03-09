package com.uri.lee.dl

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.common.model.CustomRemoteModel
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.linkfirebase.FirebaseModelSource
import com.uri.lee.dl.Utils.displaySpeechRecognizer
import com.uri.lee.dl.Utils.sendEmail
import com.uri.lee.dl.databinding.ActivityMainBinding
import com.uri.lee.dl.databinding.NewHerbDialogBinding
import com.uri.lee.dl.herbdetails.HerbDetailsActivity
import com.uri.lee.dl.hometabs.SectionsPagerAdapter
import com.uri.lee.dl.instantsearch.SPOKEN_TEXT_EXTRA
import com.uri.lee.dl.instantsearch.SearchActivity
import com.uri.lee.dl.lenscamera.CameraActivity
import com.uri.lee.dl.lensimage.ImageActivity
import com.uri.lee.dl.lensimages.ImagesActivity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val userViewModel: UserViewModel by viewModels()

    private val configViewModel: ConfigViewModel by viewModels()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        Timber.d(auth.currentUser.toString())
        if (auth.currentUser == null) {
            finishAffinity()
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    @SuppressLint("RestrictedApi")
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
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.searchView.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.microphoneView.setOnClickListener { displaySpeechRecognizer(this) }

        binding.addHerbButton.setOnClickListener {
            CustomDialogClass(this) {
                userViewModel.addHerb(it) { id ->
                    val intent = Intent(this@MainActivity, HerbDetailsActivity::class.java)
                    intent.putExtra(HERB_ID, id)
                    startActivity(intent)
                }
            }.show()
        }

        downloadModel()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configViewModel.state()
                    .mapNotNull { it.mobile }
                    .distinctUntilChanged()
                    .onEach {
                        binding.menuView.setOnClickListener { _ ->
                            val bottomSheet = BottomSheetMenu(
                                recognizedViHerbs = it.recognizedViHerbs,
                                recognizedLatinHerbs = it.recognizedLatinHerbs
                            )
                            bottomSheet.show(supportFragmentManager, "ModalBottomSheet")
                        }
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

                        val latinBundle = Bundle()
                        val viBundle = Bundle()
                        for (entry in it.recognizedLatinHerbs.entries) {
                            latinBundle.putString(entry.key, entry.value)
                        }
                        for (entry in it.recognizedViHerbs.entries) {
                            viBundle.putString(entry.key, entry.value)
                        }
                        binding.searchCameraView.setOnClickListener {
                            val intent = Intent(this@MainActivity, CameraActivity::class.java)
                            intent.putExtra(RECOGNIZED_LATIN_HERBS_KEY, latinBundle)
                            intent.putExtra(RECOGNIZED_VI_HERBS_KEY, viBundle)
                            startActivity(intent)
                        }

                        binding.searchMultiImagesView.setOnClickListener {
                            val intent = Intent(this@MainActivity, ImagesActivity::class.java)
                            intent.putExtra(RECOGNIZED_LATIN_HERBS_KEY, latinBundle)
                            intent.putExtra(RECOGNIZED_VI_HERBS_KEY, viBundle)
                            startActivity(intent)
                        }
                        binding.searchSingleImageView.setOnClickListener {
                            val intent = Intent(this@MainActivity, ImageActivity::class.java)
                            intent.putExtra(RECOGNIZED_LATIN_HERBS_KEY, latinBundle)
                            intent.putExtra(RECOGNIZED_VI_HERBS_KEY, viBundle)
                            startActivity(intent)
                        }
                    }
                    .launchIn(this)

                userViewModel.state()
                    .map { it.isAdmin }
                    .distinctUntilChanged()
                    .onEach {
                        binding.adminButton.isVisible = it
                        binding.adminButton.setOnClickListener {
                            startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onStart() {
        super.onStart()
        authUI.auth.addAuthStateListener(authStateListener)
        Utils.requestNotificationPermission(this)
    }

    @SuppressLint("RestrictedApi")
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

private class CustomDialogClass(context: Context, private val onSubmitClick: (FireStoreHerb) -> Unit) :
    Dialog(context) {

    private lateinit var binding: NewHerbDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = NewHerbDialogBinding.inflate(layoutInflater)
        val view = binding.root
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(view)

        binding.submitBtn.setOnClickListener {
            // val fields = listOf(binding.enNameView, binding.viNameView, binding.latinNameView)
            // if (fields.all { it.text.isBlank() }) { binding.instructionView.textColors = ColorStateList() }
            if (binding.viNameView.text.isBlank()) {
                binding.viNameLayoutView.error = context.getString(R.string.please_edit_this_field)
            } else {
                val newHerb = FireStoreHerb(
                    id = clock.millis(),
                    enName = binding.enNameView.text.toString(),
                    latinName = binding.latinNameView.text.toString(),
                    viName = binding.viNameView.text.toString(),
                )
                onSubmitClick(newHerb)
                dismiss()
            }
        }
    }
}

const val RECOGNIZED_LATIN_HERBS_KEY = "recognizedLatinHerbs"
const val RECOGNIZED_VI_HERBS_KEY = "recognizedLatinHerbs"
