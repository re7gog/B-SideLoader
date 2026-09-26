package dev.re7gog.b_sideloader.domain.device

import dev.re7gog.b_sideloader.domain.model.AppVersion
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
     * `BuildConfig.VERSION_CODE`. A release build derives it from its tag, so it goes up with every
     * release — which is what makes "the version code went up" proof that a self-update landed.
     */
    val versionCode: Long

    /**
     * The git tag CI built this from, which is also the name of the GitHub release it was published
     * as — the value a GitHub app's row stores as its version. [AppVersion.Unknown] for a local
     * build, which is not any release.
     */
    val releaseTag: AppVersion
}

/** Whether [app] is B-SideLoader itself, i.e. installing it replaces the running process. */
fun SelfAppInfo.isSelf(app: TrackedApp): Boolean =
    app.packageName.isNotBlank() && app.packageName == packageName
