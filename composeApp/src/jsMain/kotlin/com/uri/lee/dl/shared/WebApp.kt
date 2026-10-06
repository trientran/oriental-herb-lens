package com.uri.lee.dl.shared

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.domain.model.GeoLocation
import dev.gitlive.firebase.FirebaseOptions
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin

/** The web build's settings, from the webApp module. */
class WebConfig(
    val versionName: String,
    val isDebug: Boolean,
    /** The photo-upload Worker; empty when the build has none. */
    val photoUploadUrl: String,
    val firebase: WebFirebaseConfig,
    /** Fraud Defense (reCAPTCHA Enterprise) site key for App Check. */
    val appCheckSiteKey: String,
    /** Where the site is published, for sharing. */
    val siteUrl: String,
)

/** The Firebase web app's config (public by design: Security Rules and App Check guard access). */
class WebFirebaseConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String,
    val measurementId: String,
) {
    internal fun toOptions() = FirebaseOptions(
        applicationId = appId,
        apiKey = apiKey,
        projectId = projectId,
        storageBucket = "$projectId.appspot.com",
        gcmSenderId = senderId,
        authDomain = "$projectId.firebaseapp.com",
        gaTrackingId = measurementId,
    )
}

/** Starts the app in the page's `#app` element. */
@OptIn(ExperimentalComposeUiApi::class)
fun startWebApp(config: WebConfig) {
    startFirebase(config.firebase.toOptions(), config.appCheckSiteKey)
    val app = AppInfo(
        versionName = config.versionName,
        // The web is always the latest version: update prompts don't apply
        versionCode = Long.MAX_VALUE,
        platform = "web",
        isDebug = config.isDebug,
        photoUploadUrl = config.photoUploadUrl,
    )
    val koin = startKoin { modules(sharedModules(app)) }.koin
    koin.runStartupTasks()
    val actions = PlatformActions(
        // Photos and the camera come with identification in the browser
        pickPhotos = { it.onPicked(emptyList()) },
        requestGoogleSignIn = ::googleSignInPopup,
        onOpenStore = {},
        onExit = {},
        currentLocation = ::currentLocation,
        onShareApp = { share(config.siteUrl) },
    )
    ComposeViewport(document.getElementById("app")!!) { App(actions) }
}

/** The browser's location, after it asks; null if refused or unavailable. */
private fun currentLocation(onResult: (GeoLocation?) -> Unit) {
    val geolocation = window.navigator.asDynamic().geolocation
    if (geolocation == null) return onResult(null)
    geolocation.getCurrentPosition(
        { position: dynamic ->
            onResult(GeoLocation(latitude = position.coords.latitude as Double, longitude = position.coords.longitude as Double))
        },
        { _: dynamic -> onResult(null) },
        kotlin.js.json("enableHighAccuracy" to true, "timeout" to 15_000),
    )
}

/** The system share sheet where there is one (phones), otherwise the link goes to the clipboard. */
private fun share(url: String) {
    val navigator = window.navigator.asDynamic()
    if (navigator.share != null) {
        navigator.share(kotlin.js.json("title" to "Med Herb Lens", "url" to url))
    } else {
        navigator.clipboard?.writeText(url)
    }
}
