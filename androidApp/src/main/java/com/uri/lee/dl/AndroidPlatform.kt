package com.uri.lee.dl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.uri.lee.dl.core.ml.UriImage
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.feature.auth.GoogleCredential
import com.uri.lee.dl.feature.contribute.ContributeViewModel
import com.uri.lee.dl.shared.PlatformActions
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Android's side of [PlatformActions]: system pickers, permissions and Credential Manager.
 * Create it in onCreate: it registers activity results.
 */
class AndroidPlatform(private val activity: ComponentActivity) {

    private var onPhotos: ((List<LocalImage>) -> Unit)? = null
    private var onLocation: ((GeoLocation?) -> Unit)? = null
    private var onSpeech: ((String) -> Unit)? = null
    private val research = AndroidResearch(activity)

    private val photoPicker = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ContributeViewModel.MAX_PHOTOS),
    ) { uris -> onPhotos?.invoke(uris.map(::UriImage)) }

    private val locationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.any { it }) fetchLocation() else onLocation?.invoke(null) }

    // The answer needs no handling: the upload notifier checks the permission when it's time
    private val notificationPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val speech = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onSpeech?.invoke(it) }
    }

    fun actions() = PlatformActions(
        // Research mode, listed in Profile in every build
        research = research::platform,
        files = research.fileActions(),
        requestGoogleSignIn = ::googleSignIn,
        pickPhotos = { pick ->
            onPhotos = pick.onPicked
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        currentLocation = { onResult ->
            onLocation = onResult
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        },
        requestNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            {
                val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            null
        },
        onOpenStore = { activity.goToPlayStore() },
        onExit = {
            activity.finishAffinity()
            exitProcess(0)
        },
        onShareApp = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PLAY_STORE_URL)
            activity.startActivity(Intent.createChooser(send, null))
        },
        onOpenLanguageSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            { activity.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", activity.packageName, null))) }
        } else {
            null
        },
        onVoiceSearch = { onResult ->
            onSpeech = onResult
            speech.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
            )
        },
    )

    /** Google's account chooser; the ID token is exchanged for a Firebase session in shared code. */
    private suspend fun googleSignIn(): GoogleCredential? {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        return try {
            val result = CredentialManager.create(activity)
                .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            GoogleCredential(GoogleIdTokenCredential.createFrom(result.credential.data).idToken)
        } catch (e: GetCredentialCancellationException) {
            null
        }
    }

    /**
     * Null when location is off or no fix arrives; the screen then suggests the map instead.
     * A fix up to two minutes old will do, and a fresh one gets up to 20 seconds with GPS: asking
     * only for a quick network fix often came back empty (on Xiaomi phones, several taps in a
     * row). Failing that, the last known place.
     */
    @SuppressLint("MissingPermission") // only called once a location permission is granted
    private fun fetchLocation() {
        activity.lifecycleScope.launch {
            val client = LocationServices.getFusedLocationProviderClient(activity)
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(LOCATION_MAX_AGE_MS)
                .setDurationMillis(LOCATION_WAIT_MS)
                .build()
            val location = runCatching { client.getCurrentLocation(request, null).await() }.getOrNull()
                ?: runCatching { client.lastLocation.await() }.getOrNull()
            onLocation?.invoke(location?.let { GeoLocation(it.latitude, it.longitude) })
        }
    }

    private companion object {
        const val LOCATION_MAX_AGE_MS = 2 * 60_000L
        const val LOCATION_WAIT_MS = 20_000L
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uri.lee.dl"
    }
}
