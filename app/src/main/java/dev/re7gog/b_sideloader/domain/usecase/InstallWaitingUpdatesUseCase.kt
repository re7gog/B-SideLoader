package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.device.isSelf
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import javax.inject.Inject

/**
 * Installs the updates a background sweep left to the user ([SweepReport.waiting]) — what tapping
 * its notification does, with the app open and the user there to answer.
 *
 * What the sweep found is not trusted: by now a newer release may be out, or the update may be
 * installed already. So each app is checked again, the same way, and whatever still has an update
 * goes to [InstallCoordinator] as the user's own install — interactive, so the system can ask, with
 * pre-approval where that spares a dialog. From there it is like tapping "Update" on each: one
 * install, and so one question, at a time.
 */
class InstallWaitingUpdatesUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
    private val checkUpdates: CheckUpdatesUseCase,
    private val installCoordinator: InstallCoordinator,
    private val reconcileSelfUpdate: ReconcileSelfUpdateUseCase,
    private val selfApp: SelfAppInfo,
) {
    /** @return how many installs it started; an app already installing is not counted. */
    suspend operator fun invoke(appIds: Collection<Long>): Int {
        reconcileSelfUpdate()
        val wanted = appIds.toSet()
        val apps = appsRepository.getApps().filter { it.id in wanted }
        val updates = checkUpdates(apps).mapNotNull { outcome ->
            outcome.check?.candidate?.takeIf { outcome.hasUpdate }?.let { outcome.app to it }
        }
        // B-SideLoader's own update queued last: the scheduler holds it back until nothing else is
        // in flight, so the rest must have started by the time it is downloaded.
        val (self, others) = updates.partition { (app, _) -> selfApp.isSelf(app) }
        return (others + self).count { (app, candidate) -> installCoordinator.install(app, candidate) != null }
    }
}
