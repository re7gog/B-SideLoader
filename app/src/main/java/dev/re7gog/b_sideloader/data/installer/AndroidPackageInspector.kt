package dev.re7gog.b_sideloader.data.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.domain.installer.InstalledPackage
import dev.re7gog.b_sideloader.domain.installer.PackageChange
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import dev.re7gog.b_sideloader.data.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `PackageManager` questions, plus a live feed of package changes.
 *
 * The feed is what lets the apps list stay honest: an app removed from the system settings, or
 * updated by another store, is reflected immediately instead of on the next manual refresh.
 */
@Singleton
class AndroidPackageInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : PackageInspector {

    override fun isInstalled(packageName: String): Boolean =
        packageName.isNotBlank() && packageInfo(packageName) != null

    override fun installedVersion(packageName: String): InstalledPackage? =
        packageInfo(packageName)?.let {
            InstalledPackage(
                packageName = packageName,
                versionName = it.versionName,
                versionCode = it.versionCodeCompat(),
            )
        }

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatchingCancellable {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
    }

    /**
     * Shared so N screens observing it register one receiver, not N. `WhileSubscribed` unregisters
     * once the last collector is gone, so a backgrounded app holds no receiver at all.
     */
    override val packageChanges: Flow<PackageChange> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart
                val change = when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    -> PackageChange.Installed(packageName)

                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_FULLY_REMOVED,
                    -> PackageChange.Removed(packageName)

                    else -> return
                }
                trySend(change)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { runCatchingCancellable { context.unregisterReceiver(receiver) } }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 0)

    private fun packageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Suppress("DEPRECATION")
internal fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
