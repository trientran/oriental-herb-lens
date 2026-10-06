package com.uri.lee.dl.shared

import com.uri.lee.dl.feature.auth.GoogleCredential
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.json

// What GitLive doesn't wrap, from the Firebase JS SDK it uses (the same instance)

@JsModule("firebase/app")
@JsNonModule
private external object FirebaseAppJs {
    fun getApp(): dynamic
}

@JsModule("firebase/app-check")
@JsNonModule
private external object AppCheckJs {
    class ReCaptchaEnterpriseProvider(siteKey: String)
    fun initializeAppCheck(app: dynamic, options: dynamic): dynamic
}

@JsModule("firebase/auth")
@JsNonModule
private external object AuthJs {
    class GoogleAuthProvider {
        fun setCustomParameters(parameters: dynamic)

        companion object {
            fun credentialFromResult(result: dynamic): dynamic
        }
    }
    fun getAuth(app: dynamic): dynamic
    fun signInWithPopup(auth: dynamic, provider: dynamic): Promise<dynamic>
}

/**
 * Starts Firebase with the web app's config, and App Check with Fraud Defense (reCAPTCHA
 * Enterprise), before anything reads Firestore.
 */
internal fun startFirebase(options: FirebaseOptions, appCheckSiteKey: String) {
    Firebase.initialize(null, options)
    AppCheckJs.initializeAppCheck(
        FirebaseAppJs.getApp(),
        json("provider" to AppCheckJs.ReCaptchaEnterpriseProvider(appCheckSiteKey), "isTokenAutoRefreshEnabled" to true),
    )
}

/**
 * Google's sign-in popup. Its tokens go through the shared sign-in flow like on Android and iOS
 * (which signs in to Firebase with them, or re-authenticates before deleting the account).
 * Null when the popup is closed.
 */
internal suspend fun googleSignInPopup(): GoogleCredential? {
    val provider = AuthJs.GoogleAuthProvider()
    provider.setCustomParameters(json("prompt" to "select_account"))
    val result = try {
        AuthJs.signInWithPopup(AuthJs.getAuth(FirebaseAppJs.getApp()), provider).await()
    } catch (e: Throwable) {
        val code = e.asDynamic().code as? String
        if (code == "auth/popup-closed-by-user" || code == "auth/cancelled-popup-request") return null
        throw e
    }
    val credential = AuthJs.GoogleAuthProvider.credentialFromResult(result) ?: return null
    val idToken = credential.idToken as? String ?: return null
    return GoogleCredential(idToken = idToken, accessToken = credential.accessToken as? String)
}
