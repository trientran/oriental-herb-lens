package com.uri.lee.dl

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uri.lee.dl.shared.research.ResearchBackground

/**
 * Keeps a research run (plan Phase 7) going with the screen off or the app in the background: a
 * foreground service with a progress notification, holding a partial wake lock so the CPU stays
 * on. The run itself is in the app's research controller; this only keeps the process awake.
 */
class ResearchService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION, notification(this, intent?.getStringExtra(TEXT) ?: "Starting…", 0, 0), type)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HerbLens:research").apply {
                setReferenceCounted(false)
                acquire(MAX_RUN_MILLIS)
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15+ limits data-sync services to 6 hours a day: stop, keeping the progress to resume. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        onExpired?.invoke()
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    /** The platform side of [ResearchBackground] on Android. */
    class Background(private val context: Context) : ResearchBackground {
        override fun start(title: String, onExpired: () -> Unit) {
            Companion.onExpired = onExpired
            ContextCompat.startForegroundService(context, Intent(context, ResearchService::class.java).putExtra(TEXT, title))
        }

        override fun progress(done: Int, total: Int, text: String) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            @Suppress("MissingPermission") // checked above; the service runs either way
            NotificationManagerCompat.from(context).notify(NOTIFICATION, notification(context, text, done, total))
        }

        override fun stop() {
            onExpired = null
            context.stopService(Intent(context, ResearchService::class.java))
        }
    }

    private companion object {
        const val NOTIFICATION = 7_001
        const val CHANNEL = "research"
        const val TEXT = "text"
        const val MAX_RUN_MILLIS = 12 * 60 * 60 * 1000L
        var onExpired: (() -> Unit)? = null

        fun notification(context: Context, text: String, done: Int, total: Int): Notification {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("Research runs").build(),
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Research run")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total, done, total == 0)
                .setContentIntent(open)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
