package dev.re7gog.b_sideloader.data.installer

/**
 * User-selectable APK installation method. Replaces the old auto-prioritizing
 * mechanism (Dhizuku > Shizuku > Session) with an explicit choice.
 */
enum class InstallerMode(val displayName: String) {
    SESSION("Session (default)"),
    SHIZUKU("Shizuku / Sui"),
    DHIZUKU("Dhizuku");

    /** Whether this mode needs privileged (Shizuku/Sui/Dhizuku) services. */
    val isPrivileged: Boolean get() = this != SESSION

    /** Whether the privileged path should go through Dhizuku instead of Shizuku. */
    val useDhizuku: Boolean get() = this == DHIZUKU

    companion object {
        fun fromName(name: String?): InstallerMode =
            entries.firstOrNull { it.name == name } ?: SESSION
    }
}