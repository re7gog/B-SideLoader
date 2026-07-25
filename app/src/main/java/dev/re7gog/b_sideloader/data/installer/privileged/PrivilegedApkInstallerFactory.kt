package dev.re7gog.b_sideloader.data.installer.privileged

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.log.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a [PrivilegedApkInstaller] for the duration of one operation and always closes it.
 *
 * Shizuku's listeners are registered process-wide, so the connect/close pair has to be balanced.
 * Routing every use through [use] makes that structural instead of a convention nobody remembers
 * on the error path.
 */
@Singleton
class PrivilegedApkInstallerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) {
    suspend fun <T> use(useDhizuku: Boolean, block: suspend (PrivilegedApkInstaller) -> T): T {
        val installer = PrivilegedApkInstaller(context, useDhizuku, logger)
        installer.connect()
        return try {
            block(installer)
        } finally {
            installer.close()
        }
    }
}
