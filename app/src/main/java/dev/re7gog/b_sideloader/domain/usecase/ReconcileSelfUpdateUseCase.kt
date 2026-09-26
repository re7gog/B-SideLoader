package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.SelfApp
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SelfUpdateStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings B-SideLoader's own row in line with the build that is actually running.
 *
 * Every other install writes its version from [InstallAppUseCase], right after `PackageInstaller`
 * reports success. B-SideLoader updating itself never gets there: replacing the package kills the
 * app, so the flow, the coroutine and the process are gone before the verdict arrives. So the
 * install writes a [PendingSelfUpdate] ahead, and the *next* process decides what happened by
 * comparing its version code with the one remembered from the process before:
 *
 *  1. **Pending, and the version code went up** — the update landed. The release it was
 *     installing becomes the row's version.
 *  2. **Pending, and the version code did not go up** — it did not land: the user declined the
 *     system dialog, or the download died with the process. The row is left alone.
 *  3. **Nothing pending, but the version code changed** — this build arrived some other way: its
 *     very first start, an APK installed by hand, or an install that finished after its record had
 *     already been judged. The build's own release tag becomes the row's version, so the app
 *     always knows which release it is — and never offers the update that is already running.
 *
 * Then the running version code is remembered and the pending record dropped, in one write.
 *
 * **Once per process, and first.** Within a process the running version code never changes, so a
 * record written by *this* process — an install still in flight — would always be judged as
 * failed. Running once, before anything can start an install, means the only record ever judged
 * is one a previous process left behind. And the row it fixes is the row every update check
 * compares against: a check that read it first would show the update that was just installed.
 * Hence every such reader calls this first; after the first call, it returns immediately.
 */
@Singleton
class ReconcileSelfUpdateUseCase @Inject constructor(
    private val selfUpdates: SelfUpdateStateRepository,
    private val appsRepository: AppsRepository,
    private val selfApp: SelfAppInfo,
    private val logger: Logger,
) {
    private val mutex = Mutex()

    @Volatile
    private var reconciled = false

    /**
     * Reconciles on the first call; a concurrent call waits for it, a later one returns at once.
     *
     * Never throws. A failure is logged and counts as done for this process — retrying later could
     * judge this process's own pending install — and since nothing was settled, the next process
     * gets the same state to try again.
     */
    suspend operator fun invoke() {
        if (reconciled) return
        mutex.withLock {
            if (reconciled) return
            suspendRunCatching { reconcile() }
                .onFailure { logger.e(TAG, it) { "Could not reconcile the self-update state" } }
            reconciled = true
        }
    }

    private suspend fun reconcile() {
        val running = selfApp.versionCode
        val (remembered, pending) = selfUpdates.get()

        val recorded = if (pending != null && remembered != null && running > remembered) {
            recordInstalled(pending)
        } else {
            pending?.let { logger.i(TAG) { "Self-update to ${it.releaseName} did not land; dropping it" } }
            false
        }
        if (!recorded && running != remembered) adoptBuildTag()

        if (pending != null || running != remembered) selfUpdates.settle(running)
    }

    /** Case 1. `false` when the row the update was installed for has been deleted since. */
    private suspend fun recordInstalled(pending: PendingSelfUpdate): Boolean {
        val app = appsRepository.getApp(pending.appId)
        if (app == null) {
            logger.w(TAG) { "App ${pending.appId} is gone; nothing to record" }
            return false
        }
        if (app.version != pending.releaseName) {
            logger.i(TAG) { "Recording self-update ${app.version} -> ${pending.releaseName}" }
            appsRepository.update(app.copy(version = pending.releaseName))
        }
        return true
    }

    /**
     * Case 3: records this build's own release tag. A local build has none and writes nothing — it
     * is not any release, and claiming one would hide real updates behind it.
     */
    private suspend fun adoptBuildTag() {
        val tag = selfApp.releaseTag
        if (!tag.isKnown) return
        appsRepository.getApps()
            .filter { it.tracksThisBuild() }
            .filter { it.version != tag || it.packageName != selfApp.packageName }
            .forEach { app ->
                logger.i(TAG) { "Running $tag; recording it for ${app.name} (was '${app.version}')" }
                appsRepository.update(app.copy(version = tag, packageName = selfApp.packageName))
            }
    }

    /**
     * Only this repository names its releases after this build's tags; a fork sharing the package
     * name may not. A row with no package name yet was never installed from here, and this build
     * running is exactly that install.
     */
    private fun TrackedApp.tracksThisBuild(): Boolean =
        SelfApp.isPublishedBy(source) &&
            (packageName.isBlank() || packageName == selfApp.packageName)

    private companion object {
        const val TAG = "SelfUpdate"
    }
}
