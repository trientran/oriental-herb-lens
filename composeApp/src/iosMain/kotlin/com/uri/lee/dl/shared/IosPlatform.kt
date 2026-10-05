package com.uri.lee.dl.shared

import com.uri.lee.dl.core.designsystem.LegalLinks
import com.uri.lee.dl.core.ml.IosPickedImage
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.media.PhotoPick
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.feature.auth.AppleCredential
import com.uri.lee.dl.feature.auth.GoogleCredential
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_notify
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** iOS's side of [PlatformActions]: PHPicker, CoreLocation, the share sheet and the Swift sign-in bridges. */
internal class IosPlatform(
    private val google: GoogleSignInBridge,
    private val apple: AppleSignInBridge,
    private val host: () -> UIViewController,
) {
    // UIKit holds delegates weakly; these keep them alive while in use
    private var picker: PickerDelegate? = null
    private var location: LocationDelegate? = null

    fun actions() = PlatformActions(
        pickPhotos = ::pickPhotos,
        requestGoogleSignIn = {
            suspendCancellableCoroutine { continuation ->
                google.signInWithGoogle { idToken, accessToken, error ->
                    when {
                        error != null -> continuation.resumeWithException(IllegalStateException(error))
                        idToken != null -> continuation.resume(GoogleCredential(idToken, accessToken))
                        else -> continuation.resume(null)
                    }
                }
            }
        },
        requestAppleSignIn = {
            suspendCancellableCoroutine { continuation ->
                apple.signInWithApple { token, nonce, error ->
                    when {
                        error != null -> continuation.resumeWithException(IllegalStateException(error))
                        token != null && nonce != null -> continuation.resume(AppleCredential(token, nonce))
                        else -> continuation.resume(null)
                    }
                }
            }
        },
        onOpenStore = { open(appStoreUrl()) },
        onExit = { }, // iOS apps don't close themselves; the notice stays up
        currentLocation = ::currentLocation,
        onShareApp = {
            val share = UIActivityViewController(activityItems = listOf(appStoreUrl()), applicationActivities = null)
            topController().presentViewController(share, animated = true, completion = null)
        },
        // Settings > Med Herb Lens > Language, offered because Info.plist lists both languages
        onOpenLanguageSettings = { open(UIApplicationOpenSettingsURLString) },
    )

    private fun pickPhotos(pick: PhotoPick) {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = MAX_PHOTOS
            // JPEG rather than HEIC: the image loader that shows thumbnails can't decode HEIC
            preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCompatible
        }
        val delegate = PickerDelegate(pick.onPreparing) { photos ->
            picker = null
            pick.onPicked(photos)
        }
        picker = delegate
        val controller = PHPickerViewController(configuration).apply { this.delegate = delegate }
        topController().presentViewController(controller, animated = true, completion = null)
    }

    private fun currentLocation(onResult: (GeoLocation?) -> Unit) {
        location = LocationDelegate { found ->
            location = null
            onResult(found)
        }.also { it.start() }
    }

    private fun topController(): UIViewController {
        var top = host()
        while (true) top = top.presentedViewController ?: return top
    }

    private fun open(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
    }

    /** Info.plist's `HerbLensAppStoreId` once the app is listed; the project site until then. */
    private fun appStoreUrl(): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("HerbLensAppStoreId") as? String)?.takeIf { it.isNotBlank() }
            ?.let { "https://apps.apple.com/app/id$it" }
            ?: LegalLinks.PRIVACY_POLICY.substringBefore("/pages")

    private companion object {
        const val MAX_PHOTOS = 20L
    }
}

/**
 * Copies each picked photo to a temporary file (the picker's copies vanish when its callback
 * returns), then reports them on the main thread in the order picked.
 */
private class PickerDelegate(
    private val onPreparing: (Int) -> Unit,
    private val onResult: (List<LocalImage>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isNotEmpty()) onPreparing(results.size)
        val copies = arrayOfNulls<LocalImage>(results.size)
        val group = dispatch_group_create()
        results.forEachIndexed { index, result ->
            dispatch_group_enter(group)
            result.itemProvider.loadFileRepresentationForTypeIdentifier("public.image") { url, _ ->
                copies[index] = url?.let(::copyToTemporary)
                dispatch_group_leave(group)
            }
        }
        dispatch_group_notify(group, dispatch_get_main_queue()) { onResult(copies.filterNotNull()) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun copyToTemporary(source: NSURL): LocalImage? {
        val extension = source.pathExtension?.takeIf { it.isNotEmpty() } ?: "jpg"
        val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + NSUUID().UUIDString + "." + extension)
        val copied = NSFileManager.defaultManager.copyItemAtURL(source, target, error = null)
        return if (copied) IosPickedImage(target.absoluteString ?: return null) else null
    }
}

/** Asks for "while using" permission if needed, then for one location fix. */
@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(private val onResult: (GeoLocation?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var done = false

    fun start() {
        manager.delegate = this
        if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) manager.requestWhenInUseAuthorization()
        else request()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) request()
    }

    private fun request() {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            else -> finish(null)
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val fix = didUpdateLocations.lastOrNull() as? CLLocation
        finish(fix?.coordinate?.useContents { GeoLocation(latitude, longitude) })
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) = finish(null)

    private fun finish(result: GeoLocation?) {
        if (done) return
        done = true
        dispatch_async(dispatch_get_main_queue()) { onResult(result) }
    }
}
