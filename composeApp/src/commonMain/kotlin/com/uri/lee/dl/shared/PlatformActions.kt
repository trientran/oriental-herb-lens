package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.media.PickPhotos
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.training.Dataset
import com.uri.lee.dl.feature.training.PickedFile
import com.uri.lee.dl.feature.auth.AppleSignInRequest
import com.uri.lee.dl.feature.auth.GoogleSignInRequest
import com.uri.lee.dl.shared.research.ResearchPlatform

/** What each platform does itself: pickers, sign-in UI, sharing, store links. A null entry hides the feature. */
data class PlatformActions(
    /** Opens the system photo picker. */
    val pickPhotos: PickPhotos,
    /**
     * Whether users can share photos of a species. Not on the web (user, 6 Oct): it can't check a
     * photo shows a plant before upload, so only the apps share photos.
     */
    val sharePhotos: Boolean = true,
    val requestGoogleSignIn: GoogleSignInRequest,
    /** Sign in with Apple (iOS). */
    val requestAppleSignIn: AppleSignInRequest? = null,
    /** Revokes the app's Apple tokens when an Apple account is deleted (iOS), as Apple requires. */
    val revokeAppleToken: (suspend (authorizationCode: String) -> Unit)? = null,
    val onOpenStore: () -> Unit,
    /** Closes the app (shown when the service is suspended). */
    val onExit: () -> Unit,
    /** Asks for permission if needed and reports where the device is, or null. */
    val currentLocation: (((GeoLocation?) -> Unit) -> Unit)? = null,
    val onShareApp: (() -> Unit)? = null,
    /** Asks once for permission to show notifications, e.g. when an upload finishes in the background. */
    val requestNotificationPermission: (() -> Unit)? = null,
    val onOpenLanguageSettings: (() -> Unit)? = null,
    /** Phase 7 spike, debug builds: the labelled photos and backbones to benchmark; null hides it. */
    val trainingBenchmark: (suspend () -> BenchmarkSources)? = null,
    /** User-trained models: dataset and model-file pickers, saving a model; null hides those buttons. */
    val files: FileActions? = null,
    /** Phase 7 research mode (debug builds for now): device readings, dataset pickers, saving results; null hides it. */
    val research: (() -> ResearchPlatform)? = null,
    /** Starts speech recognition and reports what was said. */
    val onVoiceSearch: (((String) -> Unit) -> Unit)? = null,
)

/** Files for user-trained models (plan Phase 7); a null entry hides its button. */
class FileActions(
    val pickDatasetFolder: (suspend () -> Dataset?)? = null,
    val pickDatasetZip: (suspend () -> Dataset?)? = null,
    val pickModelFile: (suspend () -> PickedFile?)? = null,
    /** Hands a file to the user: save dialog, share sheet or download. */
    val saveFile: suspend (name: String, bytes: ByteArray) -> Unit,
)
