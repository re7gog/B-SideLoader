package dev.re7gog.b_sideloader.domain.device

import dev.re7gog.b_sideloader.domain.model.TrackedApp

/**
 * B-SideLoader's own identity, as the running process sees it.
 *
 * Behind an interface for the same reason as [DeviceInfo]: self-update logic has to be unit
 * testable, and `BuildConfig` is not something the domain may read.
 */
interface SelfAppInfo {
    /** `BuildConfig.APPLICATION_ID`. */
    val packageName: String

    /**
     * `BuildConfig.VERSION_NAME`. By project convention a GitHub release of this app is *named*
     * exactly this, which is what lets an installed build be compared against a release.
     */
    val versionName: String

    /** `BuildConfig.VERSION_CODE`, which Android requires to grow with every update. */
    val versionCode: Long
}

/** Whether [app] is B-SideLoader itself, i.e. installing it replaces the running process. */
fun SelfAppInfo.isSelf(app: TrackedApp): Boolean =
    app.packageName.isNotBlank() && app.packageName == packageName
