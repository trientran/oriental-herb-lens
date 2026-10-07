package com.uri.lee.dl

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.shared.modelShareNotificationText
import com.uri.lee.dl.shared.uploadNotificationText
import com.uri.lee.dl.shared.uploadsChannelName
import kotlinx.coroutines.launch

/** A notification when an upload ends while the app is in the background; tapping it opens the species. */
internal class AndroidUploadNotifier(private val context: Context, private val scope: ApplicationScope) : UploadNotifier {

    override fun uploadFinished(speciesId: Long, speciesName: String?, uploaded: Int, failed: Int) {
        if (inForeground() || !allowed()) return
        scope.launch {
            val text = uploadNotificationText(speciesId, speciesName, uploaded)
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(uploadsChannelName()).build(),
            )
            val open = PendingIntent.getActivity(
                context,
                speciesId.toInt(),
                Intent(context, MainActivity::class.java).putExtra(HERB_ID, speciesId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (allowed()) show(manager, speciesId.toInt(), notification)
        }
    }

    override fun modelShareFinished(modelName: String, shared: Boolean) {
        if (inForeground() || !allowed()) return
        scope.launch {
            val text = modelShareNotificationText(modelName, shared)
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(uploadsChannelName()).build(),
            )
            val open = PendingIntent.getActivity(
                context,
                MODEL_SHARE_ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (allowed()) show(manager, MODEL_SHARE_ID, notification)
        }
    }

    @SuppressLint("MissingPermission") // only called after allowed() checked it
    private fun show(manager: NotificationManagerCompat, id: Int, notification: android.app.Notification) = manager.notify(id, notification)

    private fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun inForeground(): Boolean {
        val process = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        return process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private companion object {
        const val CHANNEL = "uploads"

        /** Species keys are the ids of photo notifications; none is negative. */
        const val MODEL_SHARE_ID = -1
    }
}
