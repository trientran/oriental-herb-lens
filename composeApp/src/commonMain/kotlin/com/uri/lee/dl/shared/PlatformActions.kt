package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.media.PickPhotos
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.feature.auth.AppleSignInRequest
import com.uri.lee.dl.feature.auth.GoogleSignInRequest

/** What each platform does itself: pickers, sign-in UI, sharing, store links. A null entry hides the feature. */
data class PlatformActions(
    /** Opens the system photo picker. */
    val pickPhotos: PickPhotos,
    val requestGoogleSignIn: GoogleSignInRequest,
    /** Sign in with Apple (iOS). */
    val requestAppleSignIn: AppleSignInRequest? = null,
    val onOpenStore: () -> Unit,
    /** Closes the app (shown when the service is suspended). */
    val onExit: () -> Unit,
    /** Asks for permission if needed and reports where the device is, or null. */
    val currentLocation: (((GeoLocation?) -> Unit) -> Unit)? = null,
    val onShareApp: (() -> Unit)? = null,
    val onOpenLanguageSettings: (() -> Unit)? = null,
    /** Starts speech recognition and reports what was said. */
    val onVoiceSearch: (((String) -> Unit) -> Unit)? = null,
)
