package com.uri.lee.dl

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.feature.identify.IdentifyMode
import com.uri.lee.dl.lenscamera.CameraActivity
import com.uri.lee.dl.lensimage.ImageActivity
import com.uri.lee.dl.lensimages.ImagesActivity
import com.uri.lee.dl.settings.SettingsActivity
import com.uri.lee.dl.shared.App
import com.uri.lee.dl.shared.PlatformActions
import com.uri.lee.dl.upload.ImageUploadActivity
import org.koin.android.ext.android.inject
import kotlin.system.exitProcess

/**
 * Hosts the Compose app. Screens still built with Android views (scanning, uploading, sign-in)
 * are separate activities, opened through [PlatformActions]. Start it with [HERB_ID] to show a species.
 */
class MainActivity : ComponentActivity() {

    private val auth: AuthRepository by inject()
    private val openHerbId = mutableStateOf<Long?>(null)
    private var onSpeech: ((String) -> Unit)? = null

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onSpeech?.invoke(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { App(actions = platformActions(), openHerbId = openHerbId.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        Utils.requestNotificationPermission(this)
    }

    private fun handle(intent: Intent) {
        intent.getLongExtra(HERB_ID, -1).takeIf { it >= 0 }?.let { openHerbId.value = it }
    }

    private fun platformActions() = PlatformActions(
        onIdentify = { mode ->
            val screen = when (mode) {
                IdentifyMode.CAMERA -> CameraActivity::class.java
                IdentifyMode.SINGLE_IMAGE -> ImageActivity::class.java
                IdentifyMode.MULTIPLE_IMAGES -> ImagesActivity::class.java
            }
            startActivity(Intent(this, screen))
        },
        onSignIn = { startActivity(Intent(this, LoginActivity::class.java)) },
        onOpenStore = { goToPlayStore() },
        onExit = {
            finishAffinity()
            exitProcess(0)
        },
        onAddPhotos = { herbId ->
            // Contributing needs an account; browsing doesn't
            if (auth.currentUserId == null) startActivity(Intent(this, LoginActivity::class.java))
            else startActivity(Intent(this, ImageUploadActivity::class.java).putExtra(HERB_ID, herbId))
        },
        onShareApp = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PLAY_STORE_URL)
            startActivity(Intent.createChooser(send, null))
        },
        onOpenLanguageSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            { startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null))) }
        } else {
            null
        },
        onOpenCameraSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
        onVoiceSearch = { onResult ->
            onSpeech = onResult
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
            )
        },
    )

    private companion object {
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uri.lee.dl"
    }
}
