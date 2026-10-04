package dev.re7gog.b_sideloader.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Everything the user can configure, as one immutable snapshot.
 *
 * A single value object rather than a bag of independent `Flow<Boolean>`s: background scheduling
 * has to reconcile several of these at once, and reading them one by one made it easy to act on a
 * half-updated view of the settings.
 */
data class AppSettings(
    val installerMode: InstallerMode = InstallerMode.Default,
    val autoUpdate: Boolean = true,
    /** Allow update checks/downloads on a metered connection. */
    val allowMeteredNetwork: Boolean = false,
    val useDynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Default,
    /**
     * Check several apps at once, and download up to [MAX_PARALLEL_DOWNLOADS_PER_SOURCE] apps at
     * once from each source.
     *
     * Off by default: a burst of requests is what pushes an unauthenticated user over GitHub's
     * hourly quota, and the failure mode (everything rate-limited at once) is worse than a slow
     * sequential sweep. Off, apps from *different* sources still download side by side — one from
     * GitHub, one from Telegram — since they compete for nothing. Installs are one at a time
     * either way. Stored under its old key, `parallel_update_checks`, from when it only covered
     * checks.
     */
    val parallelUpdates: Boolean = false,
    /** Run a persistent foreground service instead of a periodic WorkManager job. */
    val backgroundMode: BackgroundMode = BackgroundMode.Periodic,
    val checkInterval: Duration = DEFAULT_CHECK_INTERVAL,
    /**
     * One-shot UI flag, not something the settings screen shows: whether the apps list has already
     * told the user that long-pressing a row starts a selection. It lives here because there is
     * exactly one preference store, and a second one for a single boolean would be worse.
     */
    val longPressHintSeen: Boolean = false,
) {
    companion object {
        val DEFAULT_CHECK_INTERVAL: Duration = 6.hours

        /** WorkManager refuses periodic work with a shorter interval than this. */
        val MIN_CHECK_INTERVAL: Duration = 15.minutes

        /** How many source lookups may be in flight when [parallelUpdates] is on. */
        const val MAX_PARALLEL_CHECKS: Int = 4

        /** How many downloads from one source may run at once when [parallelUpdates] is on. */
        const val MAX_PARALLEL_DOWNLOADS_PER_SOURCE: Int = 3
    }
}

/**
 * Which colour scheme the app uses, independent of the system setting.
 *
 * [System] is the default and the only value that follows `isSystemInDarkTheme()`; the other two
 * pin the scheme so a user whose phone has no per-app theme control can still force one.
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        val Default = System

        fun fromStoredName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * How update checks are kept running while the app is closed.
 *
 * The distinction matters on OEM ROMs (Xiaomi/HyperOS, Huawei, Oppo/ColorOS, vivo, Meizu, ...)
 * that kill deferred jobs aggressively: [Periodic] is cheap and battery-friendly but can be
 * silently dropped there, while [Persistent] survives because a foreground service with an ongoing
 * notification is much harder for the ROM to reap.
 */
enum class BackgroundMode {
    /** Deferred `WorkManager` job. Default; respects Doze and battery. */
    Periodic,

    /** Always-on foreground service. Reliable everywhere, costs battery and a notification. */
    Persistent,
    ;

    companion object {
        val Default = Periodic

        fun fromStoredName(name: String?): BackgroundMode =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
