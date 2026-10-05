package com.uri.lee.dl.shared

import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.domain.notification.UploadNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS suspends an app soon after it leaves the screen, so an upload asks for background time
 * (up to a few minutes), and its end is announced with a local notification if the app isn't
 * on screen.
 */
internal class IosUploadNotifier(private val scope: ApplicationScope) : UploadNotifier {
    private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

    override fun uploadStarted() {
        scope.launch(Dispatchers.Main) {
            endBackgroundTask()
            val application = UIApplication.sharedApplication
            backgroundTask = application.beginBackgroundTaskWithName("Photo upload") { endBackgroundTask() }
        }
    }

    override fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int) {
        scope.launch(Dispatchers.Main) {
            if (UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive) {
                val text = uploadNotificationText(speciesId, speciesName, uploaded)
                val content = UNMutableNotificationContent().apply {
                    setTitle(text.title)
                    setBody(text.body)
                    setUserInfo(mapOf(HERB_ID_KEY to speciesId))
                }
                UNUserNotificationCenter.currentNotificationCenter()
                    .addNotificationRequest(UNNotificationRequest.requestWithIdentifier("upload-$speciesId", content, null), null)
            }
            endBackgroundTask()
        }
    }

    private fun endBackgroundTask() {
        if (backgroundTask == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(backgroundTask)
        backgroundTask = UIBackgroundTaskInvalid
    }

    companion object {
        /** The species a notification is about, in its userInfo. */
        const val HERB_ID_KEY = "herbId"
    }
}
