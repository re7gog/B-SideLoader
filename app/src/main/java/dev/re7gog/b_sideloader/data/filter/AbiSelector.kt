package dev.re7gog.b_sideloader.data.filter

/**
 * Decides whether an APK file name can be installed on this device, by architecture. Shared by the
 * GitHub and Telegram selection logic so both sources pick the same kind of APK.
 *
 * A file runs on the device when its name carries one of the device's ABI markers, or carries no
 * known ABI marker at all (a universal build). Callers pass the device's *full* ABI list
 * ([android.os.Build.SUPPORTED_ABIS]) — not just the primary one — so a 64-bit device still accepts
 * an app that only ships the matching 32-bit split.
 */
object AbiSelector {
    /** Known Android ABI markers, used to tell an architecture-specific file from a universal one. */
    private val KNOWN_ABIS = listOf(
        "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "mips64", "mips", "riscv64"
    )

    /** True if a file with this name can be installed on a device with the given ABIs. */
    fun runsOn(fileName: String, deviceAbis: List<String>): Boolean {
        val name = fileName.lowercase()
        val abis = deviceAbis.map { it.lowercase() }
        if (abis.any { name.contains(it) }) return true  // built for one of this device's ABIs
        return KNOWN_ABIS.none { name.contains(it) }      // otherwise only if it is universal
    }
}
