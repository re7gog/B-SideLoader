package dev.re7gog.b_sideloader.data.installer.session

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.icu.util.ULocale
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.PreapprovalSession
import javax.inject.Inject

/**
 * Opens and asks for pre-approval of sessions, where [PreapprovalPolicy] says asking spares the
 * user a dialog.
 *
 * The request describes the app by its *installed* label and icon, not by the name the user gave it
 * in this app's list: the system checks the label against the APK's own, in the same locale, and
 * an installed app's label is the one thing guaranteed to match an update of it.
 */
class SessionPreapprover @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val installer: SessionApkInstaller,
    private val logger: Logger,
) {
    private val packageManager: PackageManager get() = context.packageManager

    /**
     * A session ready to be approved for [packageName], or null where asking would not help — or
     * the app is not installed, or the session could not be created.
     */
    fun open(packageName: String, mode: InstallerMode): PreapprovalSession? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val source = runCatchingCancellable { packageManager.getInstallSourceInfo(packageName) }
            .getOrNull()
            ?: return null // not installed
        val worthAsking = PreapprovalPolicy.sparesADialog(
            sdkInt = Build.VERSION.SDK_INT,
            mode = mode,
            installerOfRecord = source.installingPackageName,
            updateOwner = source.updateOwnerPackageName,
            self = context.packageName,
        )
        if (!worthAsking) return null
        return runCatchingCancellable { PreapprovalSession(installer.createSession(), packageName) }
            .onFailure { logger.w(TAG, it) { "Could not open a session for $packageName" } }
            .getOrNull()
    }

    /** Asks the user, and waits for the answer. */
    suspend fun request(session: PreapprovalSession): PreapprovalDecision {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return PreapprovalDecision.Unavailable
        }
        val details = detailsOf(session.packageName) ?: return PreapprovalDecision.Unavailable
        return installer.requestPreapproval(session.sessionId, details)
    }

    /** What the dialog shows; null when the app vanished meanwhile, or has no usable label. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun detailsOf(packageName: String): PackageInstaller.PreapprovalDetails? =
        runCatchingCancellable {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val label = info.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                ?: return@runCatchingCancellable null
            PackageInstaller.PreapprovalDetails.Builder()
                .setPackageName(packageName)
                .setLabel(label)
                // The locale the label was just resolved in, which is what the system compares.
                .setLocale(ULocale.forLocale(context.resources.configuration.locales[0]))
                .setIcon(info.loadIcon(packageManager).toBitmap(ICON_PX, ICON_PX))
                .build()
        }.onFailure { logger.w(TAG, it) { "Could not describe $packageName for pre-approval" } }
            .getOrNull()

    private companion object {
        const val TAG = "Preapproval"

        /** Plenty for the dialog's icon, and small enough to cross the binder comfortably. */
        const val ICON_PX = 192
    }
}
