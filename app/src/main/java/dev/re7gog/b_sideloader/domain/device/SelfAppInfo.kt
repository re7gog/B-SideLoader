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

    /** `BuildConfig.VERSION_CODE`, which Android requires to grow with every update. */
    val versionCode: Long

    /**
     * `PackageInfo.lastUpdateTime` for this app, or [NO_INSTALL_TIME] when it cannot be read.
     *
     * The one thing that changes on *every* install of this package, including a reinstall of the
     * very same build — which is exactly what the first install through B-SideLoader is, and what
     * a version code alone cannot detect. Only ever compared for inequality, so the value being a
     * wall-clock timestamp does not matter.
     */
    val lastUpdateTime: Long

    companion object {
        const val NO_INSTALL_TIME: Long = 0L
    }
}

/** Whether [app] is B-SideLoader itself, i.e. installing it replaces the running process. */
fun SelfAppInfo.isSelf(app: TrackedApp): Boolean =
    app.packageName.isNotBlank() && app.packageName == packageName
