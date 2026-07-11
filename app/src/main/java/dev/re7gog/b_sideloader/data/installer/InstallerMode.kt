package dev.re7gog.b_sideloader.data.installer

import androidx.annotation.StringRes
import dev.re7gog.b_sideloader.R

/**
 * User-selectable APK installation method. Replaces the old auto-prioritizing
 * mechanism (Dhizuku > Shizuku > Session) with an explicit choice.
 */
enum class InstallerMode(@param:StringRes val displayNameRes: Int) {
    SESSION(R.string.installer_session),
    SHIZUKU(R.string.installer_shizuku),
    DHIZUKU(R.string.installer_dhizuku);

    /** Whether this mode needs privileged (Shizuku/Sui/Dhizuku) services. */
    val isPrivileged: Boolean get() = this != SESSION

    /** Whether the privileged path should go through Dhizuku instead of Shizuku. */
    val useDhizuku: Boolean get() = this == DHIZUKU

    companion object {
        fun fromName(name: String?): InstallerMode =
            entries.firstOrNull { it.name == name } ?: SESSION
    }
}