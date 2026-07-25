package dev.re7gog.b_sideloader.data.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.usecase.RunUpdateSweepUseCase
import dev.re7gog.b_sideloader.domain.usecase.SweepProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Always-on update monitor, for ROMs where deferred work is not reliable.
 *
 * A foreground service with an ongoing notification is the only mechanism an app can use that
 * Xiaomi/HyperOS, Huawei, Oppo/ColorOS and friends do not routinely kill. It costs battery and a
 * permanent notification, so it is strictly opt-in — see
 * [dev.re7gog.b_sideloader.domain.model.BackgroundMode].
 */
@AndroidEntryPoint
class UpdateMonitorService : Service() {

    @Inject
    lateinit var runUpdateSweep: RunUpdateSweepUseCase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notifications: NotificationCenter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var logger: Logger

    private val scope by lazy { CoroutineScope(dispatchers.io + SupervisorJob()) }
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground(getString(R.string.notif_monitor_watching))
        // START_STICKY redelivers onStartCommand after a restart; without this guard each
        // redelivery would stack another checking loop on top of the running one.
        if (loop?.isActive != true) {
            loop = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            try {
                runCheck()
            } catch (e: Throwable) {
                e.rethrowIfCancellation()
                logger.w(TAG, e) { "Scheduled check failed; will retry next interval" }
            }
            updateNotification(getString(R.string.notif_monitor_watching))
            delay(settingsRepository.current().checkInterval)
        }
    }

    private suspend fun runCheck() {
        updateNotification(getString(R.string.notif_monitor_checking))
        val report = runUpdateSweep { progress ->
            val text = when (progress) {
                is SweepProgress.Checking -> getString(R.string.notif_monitor_checking)
                is SweepProgress.Installing ->
                    getString(R.string.notif_monitor_updating, progress.appName)
            }
            updateNotification(text)
        }
        notifications.showUpdatesAvailable(report.withUpdates - report.installed.toSet())
    }

    private fun promoteToForeground(text: String) {
        val type = when {
            // `dataSync` foreground services are capped at roughly 6 h/day from Android 14 on,
            // which would silently kill a permanent monitor; `specialUse` has no such cap and is
            // what this service is declared as in the manifest.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

            else -> 0
        }
        runCatchingCancellable {
            ServiceCompat.startForeground(
                this,
                NotificationCenter.ID_MONITOR,
                notifications.monitorNotification(text),
                type,
            )
        }.onFailure {
            logger.e(TAG, it) { "Could not enter the foreground; stopping" }
            stopSelf()
        }
    }

    private fun updateNotification(text: String) {
        notifications.notify(NotificationCenter.ID_MONITOR, notifications.monitorNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UpdateMonitor"

        /** Starts the service, tolerating the background-start restrictions of Android 12+. */
        fun start(context: Context, logger: Logger) {
            val intent = Intent(context, UpdateMonitorService::class.java)
            runCatchingCancellable { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    // Typically "app is in background" right after boot without an exemption.
                    // It will start the next time the app is opened; nothing safe to do here.
                    logger.w(TAG, it) { "Could not start the update monitor service" }
                }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UpdateMonitorService::class.java))
        }
    }
}
