package dev.re7gog.b_sideloader.data.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Parcelable
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.core.log.Logger
import javax.inject.Inject

/** API-level-safe `getParcelableExtra`. */
internal inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key)
}

/**
 * Receives `PackageInstaller` verdicts for installs started by this app and forwards them, tagged
 * with the request id the session was committed with, to whoever is waiting.
 *
 * Deliberately does *not* launch the confirmation dialog itself: the waiting coroutine does that,
 * so it stays in control of the whole sequence and can time out or be cancelled coherently.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bus: InstallEventBus

    @Inject
    lateinit var logger: Logger

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, NO_REQUEST_ID)
        logger.d(TAG) { "install result: request=$requestId status=$status" }
        bus.publish(
            PackageInstallerEvent(
                requestId = requestId,
                status = status,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                userAction = intent.parcelableExtra(Intent.EXTRA_INTENT),
            )
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "dev.re7gog.b_sideloader.INSTALL_RESULT"
        const val EXTRA_REQUEST_ID = "dev.re7gog.b_sideloader.extra.REQUEST_ID"
        const val NO_REQUEST_ID = -1
        private const val TAG = "InstallRx"
    }
}

/** Same, for uninstalls. */
@AndroidEntryPoint
class UninstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var bus: InstallEventBus

    @Inject
    lateinit var logger: Logger

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val requestId = intent.getIntExtra(
            InstallResultReceiver.EXTRA_REQUEST_ID,
            InstallResultReceiver.NO_REQUEST_ID,
        )
        logger.d(TAG) { "uninstall result: request=$requestId status=$status" }
        bus.publish(
            PackageInstallerEvent(
                requestId = requestId,
                status = status,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                userAction = intent.parcelableExtra(Intent.EXTRA_INTENT),
            )
        )
    }

    companion object {
        const val ACTION_UNINSTALL_RESULT = "dev.re7gog.b_sideloader.UNINSTALL_RESULT"
        private const val TAG = "UninstallRx"
    }
}
