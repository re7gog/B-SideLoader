package dev.re7gog.b_sideloader.domain.background

import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode

/**
 * Reconciles the platform's background machinery with the current settings.
 *
 * Implemented in `data/background` on top of WorkManager and a foreground service. Behind an
 * interface so the settings ViewModel can be unit-tested without WorkManager's static
 * initialisation, and so switching scheduler is a one-file change.
 */
interface BackgroundWorkScheduler {
    /**
     * Makes the scheduled work match [settings]: enqueue/cancel the periodic job, start/stop the
     * persistent service. Idempotent — safe to call on every settings change and on boot.
     */
    suspend fun sync(settings: AppSettings)

    /** Runs one check right now, outside the normal schedule. */
    suspend fun runOnce()
}

/**
 * What the OS and the OEM ROM currently allow this app to do in the background.
 *
 * On stock Android the answer is usually "everything"; on Xiaomi/HyperOS, Huawei, Oppo/ColorOS,
 * vivo, Meizu and Transsion ROMs, deferred work is routinely killed unless the user has granted
 * an autostart exemption that has no public API — it can only be reached by launching the ROM's
 * own settings activity. This interface exposes exactly what can be detected and what can be
 * offered, so the UI can present a checklist instead of firing blind intents.
 */
interface BackgroundRestrictions {
    val vendor: DeviceVendor

    /** Whether the app is exempt from Doze/App Standby battery optimization. */
    fun isIgnoringBatteryOptimizations(): Boolean

    /**
     * Whether the system has put the app in the "restricted" App Standby bucket, in which
     * background work essentially never runs. Always false below Android P.
     */
    fun isBackgroundRestricted(): Boolean

    /** Whether the user has notifications enabled — without them a foreground service cannot run. */
    fun areNotificationsEnabled(): Boolean

    /** Whether this ROM exposes an autostart/protected-apps screen we can open. */
    fun hasAutoStartSettings(): Boolean

    /** Opens the battery-optimization exemption prompt. False when it could not be launched. */
    fun requestIgnoreBatteryOptimizations(): Boolean

    /** Opens the ROM's autostart/protected-apps screen. False when it could not be launched. */
    fun openAutoStartSettings(): Boolean

    /** Opens this app's system settings page as a last resort. */
    fun openAppSettings(): Boolean

    /** Opens the system notification settings for this app. */
    fun openNotificationSettings(): Boolean
}

/**
 * Phone vendors that need their own background handling. Grouped by the ROM rather than the brand,
 * because e.g. Redmi and POCO both run MIUI/HyperOS and share its autostart manager.
 */
enum class DeviceVendor(
    /** `Build.MANUFACTURER` values, lower-cased, that map to this ROM. */
    val manufacturers: Set<String>,
    /** Whether this ROM is known to kill deferred background work. */
    val restrictsBackgroundAggressively: Boolean,
) {
    Xiaomi(setOf("xiaomi", "redmi", "poco"), true),
    Huawei(setOf("huawei", "honor"), true),
    Oppo(setOf("oppo", "realme", "oneplus"), true),
    Vivo(setOf("vivo", "iqoo"), true),
    Meizu(setOf("meizu"), true),
    Transsion(setOf("infinix", "tecno", "itel"), true),
    Asus(setOf("asus"), true),
    Samsung(setOf("samsung"), true),
    Other(emptySet(), false),
    ;

    companion object {
        fun fromManufacturer(manufacturer: String): DeviceVendor {
            val normalized = manufacturer.lowercase().trim()
            return entries.firstOrNull { normalized in it.manufacturers } ?: Other
        }
    }
}

/** A snapshot of everything that could stop background updates from running. */
data class BackgroundHealth(
    val vendor: DeviceVendor,
    val mode: BackgroundMode,
    val ignoresBatteryOptimizations: Boolean,
    val isBackgroundRestricted: Boolean,
    val notificationsEnabled: Boolean,
    val autoStartSettingsAvailable: Boolean,
) {
    val issues: List<BackgroundIssue>
        get() = buildList {
            if (isBackgroundRestricted) add(BackgroundIssue.BackgroundRestricted)
            if (!ignoresBatteryOptimizations) add(BackgroundIssue.BatteryOptimized)
            if (!notificationsEnabled) add(BackgroundIssue.NotificationsBlocked)
            if (vendor.restrictsBackgroundAggressively) add(BackgroundIssue.AutoStartUnknown)
            if (vendor.restrictsBackgroundAggressively && mode == BackgroundMode.Periodic) {
                add(BackgroundIssue.PeriodicUnreliableOnThisRom)
            }
        }

    /** Nothing detected that would stop updates. */
    val isHealthy: Boolean get() = issues.isEmpty()
}

/**
 * One actionable problem. [AutoStartUnknown] is deliberately not a failure — no ROM lets an app
 * query its own autostart state, so the UI can only tell the user where the switch is and let them
 * confirm they flipped it.
 */
enum class BackgroundIssue {
    /** The system put the app in the restricted bucket. */
    BackgroundRestricted,

    /** The app is subject to Doze/App Standby. */
    BatteryOptimized,

    /** Notifications are off, so the persistent mode cannot show its ongoing notification. */
    NotificationsBlocked,

    /** ROM has an autostart allowlist whose state cannot be read. */
    AutoStartUnknown,

    /** Deferred jobs are unreliable on this ROM; the persistent service is the safer choice. */
    PeriodicUnreliableOnThisRom,
}
