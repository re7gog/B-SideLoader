package dev.re7gog.b_sideloader.data.device

import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place `BuildConfig` is read for identity purposes.
 *
 * Deliberately not `PackageManager`: these three values describe the code that is *running*, which
 * after a self-update is exactly what the confirmation logic needs to compare against the record
 * the previous version left behind.
 */
@Singleton
class AndroidSelfAppInfo @Inject constructor() : SelfAppInfo {

    override val packageName: String = BuildConfig.APPLICATION_ID

    override val versionName: String = BuildConfig.VERSION_NAME

    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
}
