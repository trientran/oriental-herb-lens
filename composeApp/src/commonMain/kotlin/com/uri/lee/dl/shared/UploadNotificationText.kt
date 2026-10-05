package com.uri.lee.dl.shared

import com.uri.lee.dl.core.designsystem.resources.Res
import com.uri.lee.dl.core.designsystem.resources.notify_upload_done
import com.uri.lee.dl.core.designsystem.resources.notify_upload_done_title
import com.uri.lee.dl.core.designsystem.resources.notify_upload_failed
import com.uri.lee.dl.core.designsystem.resources.notify_upload_failed_title
import com.uri.lee.dl.core.designsystem.resources.notify_uploads_channel
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** A notification's title and text, in the app's language; the same on Android and iOS. */
data class NotificationText(val title: String, val body: String)

/** What the upload notification says. [speciesName] falls back to the species key. */
suspend fun uploadNotificationText(speciesId: Long, speciesName: String?, uploaded: Int): NotificationText {
    val name = speciesName ?: speciesId.toString()
    return if (uploaded > 0) {
        NotificationText(getString(Res.string.notify_upload_done_title), getPluralString(Res.plurals.notify_upload_done, uploaded, uploaded, name))
    } else {
        NotificationText(getString(Res.string.notify_upload_failed_title), getString(Res.string.notify_upload_failed, name))
    }
}

/** The name of Android's notification channel for uploads. */
suspend fun uploadsChannelName(): String = getString(Res.string.notify_uploads_channel)
