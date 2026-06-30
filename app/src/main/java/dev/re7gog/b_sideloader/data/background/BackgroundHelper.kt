package dev.re7gog.b_sideloader.data.background

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

@SuppressLint("BatteryLife")
fun requestBatteryOptimizationExemption(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName

    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun openAutostartSettings(context: Context) {
    val launchIntents = listOf(
        // 1. Xiaomi / Redmi / Poco (MIUI & HyperOS)
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),

        // 2. Huawei / Honor (EMUI, HarmonyOS, MagicOS)
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.appmanager.views.permapp.activity.AppPermGroupsActivity")),

        // 3. Oppo / Realme / OnePlus (ColorOS, Realme UI, OxygenOS)
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startupapp.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")),
        Intent().setComponent(ComponentName("com.oppo.safecenter", "com.oppo.safecenter.permission.startup.StartupAppListActivity")),

        // 4. Vivo / iQOO (OriginOS, FuntouchOS)
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),

        // 5. Infinix / Tecno / Itel (XOS, HiOS, Phone Master)
        Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.AppListActivity")),
        Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.shortcut.ShortcutPermissionActivity")),

        // 6. Meizu (Flyme)
        Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
        Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.PermissionMainActivity")),

        // 7. Asus
        Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
        Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
    )

    for (intent in launchIntents) {
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    try {
        val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(batteryIntent)
        return
    } catch (_: Exception) {
        // Ignore
    }

    try {
        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(appInfoIntent)
    } catch (_: Exception) {
        // Fail
    }
}