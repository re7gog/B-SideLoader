package dev.re7gog.b_sideloader.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place this app's own identity is read.
 *
 * The first two values come from `BuildConfig`, so they describe the code that is *running* —
 * which after a self-update is precisely what has to be compared against the record the previous
 * version left behind. [lastUpdateTime] cannot come from there and is re-read on every access:
 * caching it would freeze the value the process started with, and the whole point is to notice
 * that the package underneath has been replaced.
 */
@Singleton
class AndroidSelfAppInfo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SelfAppInfo {

    override val packageName: String = BuildConfig.APPLICATION_ID

    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()

    override val lastUpdateTime: Long
        get() = try {
            packageInfo().lastUpdateTime
        } catch (_: PackageManager.NameNotFoundException) {
            // Cannot happen for our own package, but a missing timestamp must not throw: the
            // caller falls back to the version code, which is strictly better than crashing.
            SelfAppInfo.NO_INSTALL_TIME
        }

    private fun packageInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
    }
}
