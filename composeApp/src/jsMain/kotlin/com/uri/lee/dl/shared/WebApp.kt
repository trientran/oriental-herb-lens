package com.uri.lee.dl.shared

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.window.ComposeViewport
import com.uri.lee.dl.core.common.AppInfo
import dev.gitlive.firebase.FirebaseOptions
import com.uri.lee.dl.core.ml.WebLocalImage
import com.uri.lee.dl.domain.media.PhotoPick
import com.uri.lee.dl.domain.notification.UploadNotifier
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement

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
        // A stand-in for the visitor's country, which the browser doesn't know
        inVietnam = js("Intl.DateTimeFormat().resolvedOptions().timeZone") as? String in setOf("Asia/Ho_Chi_Minh", "Asia/Saigon"),
    )
    val koin = startKoin {
        modules(sharedModules(app) + module { single<UploadNotifier> { WebUploadNotifier() } })
    }.koin
    koin.runStartupTasks()
    val actions = PlatformActions(
        // Photos are for identifying only: sharing them is in the apps (D19)
        pickPhotos = ::pickPhotos,
        sharePhotos = false,
        requestGoogleSignIn = ::googleSignInPopup,
        onOpenStore = {},
        onExit = {},
        onShareApp = { share(config.siteUrl) },
    )
    ignoreCancelledRequests()
    ComposeViewport(document.getElementById("app")!!) {
        CompositionLocalProvider(LocalUriHandler provides WebUriHandler) {
            // On the web any text can be selected and copied, e.g. a name to search elsewhere
            SelectionContainer { App(actions) }
        }
    }
}

/** Links open in a new tab; mailto: links go to the mail app (through a link, without a blank tab). */
private object WebUriHandler : UriHandler {
    override fun openUri(uri: String) {
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = uri
        if (!uri.startsWith("mailto:")) {
            link.target = "_blank"
            link.rel = "noopener"
        }
        link.click()
    }
}

/**
 * Leaving a page cancels its requests; Ktor then ends the half-read response with the browser's
 * AbortError outside any app code. It only means "cancelled", so it isn't reported as an error.
 */
private fun ignoreCancelledRequests() {
    val isAbort = { error: dynamic -> error != null && error.name == "AbortError" }
    window.addEventListener("error", { event -> if (isAbort(event.asDynamic().error)) event.preventDefault() })
    window.addEventListener("unhandledrejection", { event -> if (isAbort(event.asDynamic().reason)) event.preventDefault() })
}

/** The browser's file chooser, for images; on phones it also offers the camera. */
private fun pickPhotos(pick: PhotoPick) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    input.multiple = true
    input.onchange = {
        val files = input.files
        val photos = (0 until (files?.length ?: 0)).mapNotNull { files?.item(it) }.take(MAX_PHOTOS).map(::WebLocalImage)
        pick.onPicked(photos)
    }
    // Chrome and Safari report a cancelled chooser
    input.addEventListener("cancel", { pick.onPicked(emptyList()) })
    input.click()
}

/** The system share sheet where there is one (phones), otherwise the link goes to the clipboard. */
private fun share(url: String) {
    val navigator = window.navigator.asDynamic()
    if (navigator.share != null) {
        // Rejected when the visitor closes the share sheet: nothing to do
        navigator.share(kotlin.js.json("title" to "Med Herb Lens", "url" to url)).catch { _: dynamic -> }
    } else {
        navigator.clipboard?.writeText(url)
    }
}

/** As many as the iOS picker allows. */
private const val MAX_PHOTOS = 20
