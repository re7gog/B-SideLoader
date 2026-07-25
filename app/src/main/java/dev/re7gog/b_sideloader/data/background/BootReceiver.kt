package dev.re7gog.b_sideloader.data.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.di.ApplicationScope
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Re-establishes background update checking after a reboot or an app update.
 *
 * WorkManager restores its own periodic jobs, but a foreground service does not restart itself —
 * and on ROMs that clear everything on boot, re-syncing here is what keeps the persistent monitor
 * alive at all.
 *
 * Uses the application-wide scope rather than an ad-hoc `CoroutineScope(Dispatchers.Default)`: a
 * scope created inside `onReceive` has no parent, so nothing can cancel it and a hung sync would
 * leak the process for as long as the system keeps it around.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncBackgroundWork: SyncBackgroundWorkUseCase

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var logger: Logger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                withContext(dispatchers.default) {
                    suspendRunCatching { syncBackgroundWork() }
                        .onFailure { logger.e(TAG, it) { "Could not reschedule updates on boot" } }
                }
            } finally {
                // Must always run, or the system holds this receiver's wake lock until it times out.
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Non-standard, but HTC and older MIUI builds send this instead of BOOT_COMPLETED.
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
