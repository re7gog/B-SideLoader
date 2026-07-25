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
    /** Run a persistent foreground service instead of a periodic WorkManager job. */
    val backgroundMode: BackgroundMode = BackgroundMode.Periodic,
    val checkInterval: Duration = DEFAULT_CHECK_INTERVAL,
) {
    companion object {
        val DEFAULT_CHECK_INTERVAL: Duration = 6.hours

        /** WorkManager refuses periodic work with a shorter interval than this. */
        val MIN_CHECK_INTERVAL: Duration = 15.minutes
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
