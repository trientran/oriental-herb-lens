package com.uri.lee.dl

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uri.lee.dl.domain.upload.UploadScheduler
import com.uri.lee.dl.shared.PendingUploads
import com.uri.lee.dl.shared.uploadingNotificationText
import com.uri.lee.dl.shared.uploadsChannelName
import java.util.concurrent.TimeUnit

/**
 * Runs the saved uploads (photos, shared models) through WorkManager, which keeps the work after
 * the app is swiped away or the device restarts, waits for a connection, and tries again with
 * backoff while anything is left.
 */
class PendingUploadsWorker(
    context: Context,
    params: WorkerParameters,
    private val uploads: PendingUploads,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        uploads.run()
        return if (uploads.hasPending()) Result.retry() else Result.success()
    }

    /** Expedited work runs as a foreground service on Android 11 and older, which needs a notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName(uploadsChannelName()).build(),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(uploadingNotificationText())
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "uploads"
        const val NOTIFICATION_ID = -2
    }
}

/** Android's [UploadScheduler]: one unique piece of work; a new upload while it runs queues another run after it. */
internal class WorkManagerUploadScheduler(private val context: Context) : UploadScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<PendingUploadsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("pending-uploads", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
