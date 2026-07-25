package dev.re7gog.b_sideloader.data.background

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.background.BackgroundRestrictions
import dev.re7gog.b_sideloader.domain.background.DeviceVendor
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device actually lets the app do in the background, and how to ask for more.
 *
 * The hard part is the OEM autostart allowlists. They have no API — not to read, not to request —
 * so the only thing an app can do is open the ROM's own screen and let the user flip the switch.
 * The previous implementation fired a hard-coded list of `ComponentName`s in sequence and hoped;
 * this version resolves the candidates for the *detected vendor only*, verifies the target exists
 * before launching it, and reports back whether anything was actually opened so the UI can fall
 * back to written instructions instead of silently doing nothing.
 */
@Singleton
class AndroidBackgroundRestrictions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    deviceInfo: DeviceInfo,
    private val logger: Logger,
) : BackgroundRestrictions {

    override val vendor: DeviceVendor = DeviceVendor.fromManufacturer(deviceInfo.manufacturer)

    private val powerManager get() = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun isIgnoringBatteryOptimizations(): Boolean =
        runCatchingCancellable { powerManager.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)

    override fun isBackgroundRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return runCatchingCancellable { activityManager.isBackgroundRestricted }.getOrDefault(false)
    }

    override fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun hasAutoStartSettings(): Boolean = autoStartIntent() != null

    @SuppressLint("BatteryLife")
    override fun requestIgnoreBatteryOptimizations(): Boolean {
        if (isIgnoringBatteryOptimizations()) return true
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        return launch(intent)
    }

    override fun openAutoStartSettings(): Boolean {
        val intent = autoStartIntent() ?: return false
        return launch(intent)
    }

    override fun openAppSettings(): Boolean = launch(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    )

    override fun openNotificationSettings(): Boolean = launch(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    /** The first autostart screen for this vendor that actually exists on this device. */
    private fun autoStartIntent(): Intent? = autoStartCandidates(vendor)
        .map { (pkg, activity) -> Intent().setComponent(ComponentName(pkg, activity)) }
        .firstOrNull { it.resolvesOnThisDevice() }

    private fun Intent.resolvesOnThisDevice(): Boolean =
        runCatchingCancellable {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(this, 0) != null
        }.getOrDefault(false)

    private fun launch(intent: Intent): Boolean = runCatchingCancellable {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        logger.w(TAG, it) { "Could not open ${intent.component ?: intent.action}" }
    }.isSuccess

    /**
     * Known autostart / "protected apps" screens, per ROM.
     *
     * Several entries per vendor because the activity was renamed across ROM versions; the first
     * one that resolves wins. Only the detected vendor's entries are tried, so an unrelated ROM
     * can never be sent to a screen that happens to share a package name.
     */
    private fun autoStartCandidates(vendor: DeviceVendor): List<Pair<String, String>> = when (vendor) {
        DeviceVendor.Xiaomi -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )

        DeviceVendor.Huawei -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        )

        DeviceVendor.Oppo -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        )

        DeviceVendor.Vivo -> listOf(
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        )

        DeviceVendor.Meizu -> listOf(
            "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC",
            "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
        )

        DeviceVendor.Transsion -> listOf(
            "com.transsion.phonemaster" to "com.itel.autostart.AutoStartActivity",
            "com.transsion.phonemaster" to "com.transsion.phonemaster.AppListActivity",
        )

        DeviceVendor.Asus -> listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        )

        // Samsung's "Sleeping apps" list is inside Device Care and has no launchable component;
        // the battery-optimization exemption plus written guidance is all that can be offered.
        DeviceVendor.Samsung,
        DeviceVendor.Other,
        -> emptyList()
    }

    private companion object {
        const val TAG = "BgRestrictions"
    }
}
