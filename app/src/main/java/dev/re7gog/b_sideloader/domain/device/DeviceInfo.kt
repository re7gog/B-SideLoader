package dev.re7gog.b_sideloader.domain.device

/**
 * The handful of device facts the domain needs. Behind an interface so selection logic can be
 * unit-tested for an arm64 phone, an x86 emulator or an API 26 device without Robolectric.
 */
interface DeviceInfo {
    /** `Build.SUPPORTED_ABIS`, most preferred first. */
    val supportedAbis: List<String>

    /** `Build.VERSION.SDK_INT`. */
    val sdkInt: Int

    /** Lower-cased `Build.MANUFACTURER`. */
    val manufacturer: String

    /** `Build.MODEL`. */
    val model: String

    /**
     * Whether this ROM is known to kill deferred background work aggressively. Drives whether the
     * app nudges the user towards the persistent-service mode and the autostart allowlist.
     */
    val hasAggressiveBackgroundLimits: Boolean

    /**
     * `true` from Android 12 (S) on, where a `PackageInstaller` session may declare
     * `USER_ACTION_NOT_REQUIRED` and update an app it installed itself without a prompt.
     */
    val supportsSilentSelfUpdates: Boolean
}
