package dev.re7gog.b_sideloader.data.installer.session

import android.os.Build

/**
 * What decides whether a standard `PackageInstaller` session updating [packageName] stops for the
 * user. Read from the device by [UpdateFactsReader]; judged by [UserActionPolicy].
 */
data class UpdateFacts(
    val sdkInt: Int,
    /** This app's package name. */
    val self: String,
    val packageName: String,
    val installed: Boolean,
    /** The installed app's `installingPackageName`; null when not recorded, or not installed. */
    val installerOfRecord: String? = null,
    /** The installed app's `updateOwnerPackageName` (Android 14+); null when nobody claimed it. */
    val updateOwner: String? = null,
    /** Whether `UPDATE_PACKAGES_WITHOUT_USER_ACTION` is granted. */
    val mayUpdateWithoutUserAction: Boolean,
    /** Whether the user lets this app install apps at all ("Install unknown apps"). */
    val mayRequestInstalls: Boolean,
)

/**
 * Whether committing a session with `USER_ACTION_NOT_REQUIRED` will still ask the user — the rule
 * `PackageInstallerSession.computeUserActionRequirement` applies, restated so it can be asked
 * *before* anything is downloaded. Pure, so it is pinned down by plain unit tests.
 *
 * It does not ask only for an update — on Android 12+, with the permission granted and installs
 * from this app allowed — of this app itself, or of an app it is responsible for: the update owner
 * when one is recorded (Android 14), the installer of record otherwise. An update owner that is
 * someone else always asks, whoever installed the app.
 *
 * One condition is left out, because it is only known once the APK is: the system also wants the
 * APK to target a recent SDK (the floor rises with each Android version). An update this predicts
 * to be silent can still ask; whoever cannot show the dialog must be ready for that.
 */
internal object UserActionPolicy {

    fun isRequired(facts: UpdateFacts): Boolean = with(facts) {
        if (sdkInt < Build.VERSION_CODES.S) return true
        if (!installed) return true
        if (updateOwner != null && updateOwner != self) return true
        if (!mayRequestInstalls || !mayUpdateWithoutUserAction) return true
        val responsible = packageName == self ||
            if (updateOwner != null) updateOwner == self else installerOfRecord == self
        !responsible
    }
}
