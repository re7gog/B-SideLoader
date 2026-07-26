package dev.re7gog.b_sideloader.data.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.MainActivity
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every notification this app posts, and the channels they live on.
 *
 * Injected rather than a global object so a test can assert what would be shown, and so the
 * channel setup happens exactly once at startup instead of being re-run defensively.
 */
@Singleton
class NotificationCenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the channels. Idempotent; called from `Application.onCreate`. */
    fun ensureChannels() {
        val channels = listOf(
            channel(
                id = CHANNEL_UPDATES_AVAILABLE,
                name = R.string.notif_channel_alerts_name,
                description = R.string.notif_channel_alerts_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
            channel(
                id = CHANNEL_UPDATE_PROGRESS,
                name = R.string.notif_channel_progress_name,
                description = R.string.notif_channel_progress_desc,
                importance = NotificationManager.IMPORTANCE_LOW,
            ),
            channel(
                id = CHANNEL_MONITOR,
                name = R.string.notif_channel_monitor_name,
                description = R.string.notif_channel_monitor_desc,
                // MIN so the persistent service's ongoing notification stays out of the way.
                importance = NotificationManager.IMPORTANCE_MIN,
            ),
        )
        runCatchingCancellable { manager.createNotificationChannels(channels) }
            .onFailure { logger.w(TAG, it) { "Could not create notification channels" } }
    }

    /** Ongoing, low-key notification for the persistent monitoring service. */
    fun monitorNotification(text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle(context.getString(R.string.notif_monitor_watching))
            .setContentText(text)
            .setContentIntent(openAppIntent(REQUEST_OPEN))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    /** Determinate progress while an update is downloading/installing. */
    fun progressNotification(appName: String?, percent: Int): Notification {
        val name = appName ?: context.getString(R.string.notif_apps_generic)
        return NotificationCompat.Builder(context, CHANNEL_UPDATE_PROGRESS)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(context.getString(R.string.notif_progress_text, name))
            .setOngoing(true)
            .setSilent(true)
            .setProgress(PROGRESS_MAX, percent.coerceIn(0, PROGRESS_MAX), percent < 0)
            .build()
    }

    /** "Updates are available" alert, for when this app cannot install them silently. */
    fun showUpdatesAvailable(appNames: List<String>) {
        if (appNames.isEmpty()) return
        val summary = appNames.joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_AVAILABLE)
            .setSmallIcon(R.drawable.update_24px)
            .setContentTitle(context.getString(R.string.notif_update_available_title))
            .setContentText(context.getString(R.string.notif_update_available_text, summary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openAppIntent(REQUEST_UPDATE, requestUpdate = true))
            .setAutoCancel(true)
            .build()
        notify(ID_UPDATES_AVAILABLE, notification)
    }

    fun cancelProgress() = cancel(ID_UPDATE_PROGRESS)

    /**
     * Posts a notification, or drops it when the app may not.
     *
     * Two separate failure modes, hence two guards. `POST_NOTIFICATIONS` is a runtime permission
     * from Android 13 on, and posting without it is a silent no-op — worth a log line, because
     * "the update notification never appeared" is otherwise indistinguishable from "the sweep
     * never ran". Below Android 13 the permission is implicitly held. Separately, some OEM ROMs
     * throw from `notify` even with the permission granted, which is why the call itself stays
     * wrapped.
     */
    fun notify(id: Int, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            logger.w(TAG) { "Notification $id dropped: POST_NOTIFICATIONS is not granted" }
            return
        }
        runCatchingCancellable { manager.notify(id, notification) }
            .onFailure { logger.w(TAG, it) { "Could not post notification $id" } }
    }

    fun cancel(id: Int) {
        runCatchingCancellable { manager.cancel(id) }
    }

    private fun channel(
        id: String,
        name: Int,
        description: Int,
        importance: Int,
    ) = NotificationChannel(id, context.getString(name), importance).apply {
        this.description = context.getString(description)
    }

    private fun openAppIntent(requestCode: Int, requestUpdate: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (requestUpdate) putExtra(MainActivity.EXTRA_RUN_UPDATE_CHECK, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_UPDATES_AVAILABLE = "update_alerts_channel"
        const val CHANNEL_UPDATE_PROGRESS = "update_progress_channel"
        const val CHANNEL_MONITOR = "update_monitor_channel"

        /** Stable ids: reusing them means a new state replaces the old one instead of stacking. */
        const val ID_MONITOR = 424_242
        const val ID_UPDATE_PROGRESS = 424_243
        const val ID_UPDATES_AVAILABLE = 424_244

        private const val TAG = "Notifications"
        private const val PROGRESS_MAX = 100
        private const val REQUEST_OPEN = 0
        private const val REQUEST_UPDATE = 1
    }
}
