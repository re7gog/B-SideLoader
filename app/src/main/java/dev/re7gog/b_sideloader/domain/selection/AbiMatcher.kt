package dev.re7gog.b_sideloader.domain.selection

/**
 * Decides whether an APK file name can be installed on this device, by architecture.
 *
 * A file runs on the device when its name carries one of the device's ABI markers, or carries no
 * known ABI marker at all (a universal build). Callers pass the device's *full* ABI list
 * (`Build.SUPPORTED_ABIS`) — not just the primary one — so a 64-bit device still accepts an app
 * that only ships the matching 32-bit split.
 */
object AbiMatcher {

    /**
     * Known Android ABI markers, longest first so that scanning for `x86_64` cannot be
     * short-circuited by the `x86` prefix, or `arm64-v8a` by `armeabi`.
     */
    private val KNOWN_ABIS = listOf(
        "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "mips64", "mips", "riscv64",
    )

    /** True when a file with this name can be installed on a device with the given ABIs. */
    fun runsOn(fileName: String, deviceAbis: List<String>): Boolean {
        val name = fileName.lowercase()
        val supported = deviceAbis.map { it.lowercase() }
        if (supported.any { name.contains(it) }) return true // built for one of this device's ABIs
        return KNOWN_ABIS.none { name.contains(it) } // otherwise only if it is universal
    }

    /**
     * Picks the first installable entry, falling back to the first entry overall.
     *
     * The fallback matters: when every candidate is an ABI split for another architecture there is
     * nothing installable, and offering *something* lets the user see and adjust their filters
     * rather than staring at an empty screen.
     */
    fun <T> pickInstallable(
        candidates: List<T>,
        deviceAbis: List<String>,
        fileNameOf: (T) -> String,
    ): T? = candidates.firstOrNull { runsOn(fileNameOf(it), deviceAbis) } ?: candidates.firstOrNull()
}
