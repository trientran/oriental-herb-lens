package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.feature.auth.GoogleIdTokenRequest

/** What each platform does itself: pickers, sign-in UI, sharing, store links. A null entry hides the feature. */
data class PlatformActions(
    /** Opens the system photo picker and reports the photos chosen (none if cancelled). */
    val pickPhotos: ((List<LocalImage>) -> Unit) -> Unit,
    val requestGoogleIdToken: GoogleIdTokenRequest,
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
