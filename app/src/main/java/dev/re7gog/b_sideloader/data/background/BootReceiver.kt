package dev.re7gog.b_sideloader.data.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-establishes background update checking after a reboot. WorkManager restores its own
 * periodic jobs, but a foreground service does not restart itself — and on OEM ROMs that
 * kill everything on boot, re-syncing here is what keeps the persistent updater alive.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var updateScheduler: UpdateScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&  // HTC/older MIUI
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateScheduler.sync()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule updates on boot: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
