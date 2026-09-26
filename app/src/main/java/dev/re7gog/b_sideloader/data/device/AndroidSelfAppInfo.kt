package dev.re7gog.b_sideloader.data.device

import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.model.AppVersion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place this app's own identity is read.
 *
 * Everything comes from `BuildConfig`, so it describes the code that is *running* — which after a
 * self-update is precisely what has to be compared against what the previous version left behind.
 * `RELEASE_TAG` is injected by CI; see `app/build.gradle.kts`.
 */
@Singleton
class AndroidSelfAppInfo @Inject constructor() : SelfAppInfo {

    override val packageName: String = BuildConfig.APPLICATION_ID

    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()

    override val releaseTag: AppVersion = AppVersion(BuildConfig.RELEASE_TAG)
}
