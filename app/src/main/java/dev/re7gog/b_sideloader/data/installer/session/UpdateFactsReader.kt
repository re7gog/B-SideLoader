package dev.re7gog.b_sideloader.data.installer.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import javax.inject.Inject

/** Reads the [UpdateFacts] for a package off the device. */
class UpdateFactsReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager get() = context.packageManager

    fun read(packageName: String): UpdateFacts {
        val source = installSource(packageName)
        return UpdateFacts(
            sdkInt = Build.VERSION.SDK_INT,
            self = context.packageName,
            packageName = packageName,
            installed = source != null,
            installerOfRecord = source?.installer,
            updateOwner = source?.updateOwner,
            mayUpdateWithoutUserAction = mayUpdateWithoutUserAction(),
            mayRequestInstalls = packageManager.canRequestPackageInstalls(),
        )
    }

    /** Who installed [packageName] and owns its updates; null when it is not installed. */
    private fun installSource(packageName: String): Source? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Before Android 11 every update asks anyway; only whether it is installed matters.
            return runCatchingCancellable { packageManager.getPackageInfo(packageName, 0) }
                .getOrNull()
                ?.let { Source(installer = null, updateOwner = null) }
        }
        val info = runCatchingCancellable { packageManager.getInstallSourceInfo(packageName) }.getOrNull()
            ?: return null
        val owner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.updateOwnerPackageName
        } else {
            null
        }
        return Source(installer = info.installingPackageName, updateOwner = owner)
    }

    private fun mayUpdateWithoutUserAction(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION) ==
            PackageManager.PERMISSION_GRANTED

    private class Source(val installer: String?, val updateOwner: String?)
}
