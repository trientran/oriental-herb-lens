package com.uri.lee.dl.shared

import com.uri.lee.dl.feature.identify.IdentifyMode

/**
 * What each platform does itself: screens not yet in Compose, sharing, store links. A null entry
 * hides the feature on that platform.
 */
data class PlatformActions(
    val onIdentify: (IdentifyMode) -> Unit,
    val onSignIn: () -> Unit,
    val onOpenStore: () -> Unit,
    /** Closes the app (shown when the service is suspended). */
    val onExit: () -> Unit,
    val onAddPhotos: ((Long) -> Unit)? = null,
    val onShareApp: (() -> Unit)? = null,
    val onOpenLanguageSettings: (() -> Unit)? = null,
    val onOpenCameraSettings: (() -> Unit)? = null,
    /** Starts speech recognition and reports what was said. */
    val onVoiceSearch: (((String) -> Unit) -> Unit)? = null,
)
