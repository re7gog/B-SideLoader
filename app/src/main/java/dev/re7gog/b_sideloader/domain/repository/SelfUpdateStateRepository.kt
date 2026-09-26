package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.SelfUpdateState

/**
 * The [SelfUpdateState] kept between processes.
 *
 * There is at most one pending update: self-updates are installed one at a time, and a newer
 * attempt supersedes whatever the last one left behind.
 *
 * Unlike the other repositories, none of these throw
 * [dev.re7gog.b_sideloader.domain.error.AppError]: the state is a best-effort hint, and failing an
 * install — or app start — because a preference could not be written would be a strictly worse
 * outcome than missing one version write.
 */
interface SelfUpdateStateRepository {

    suspend fun get(): SelfUpdateState

    /** Records [pending] as about to be installed over the running build. */
    suspend fun markPending(pending: PendingSelfUpdate)

    /** Forgets the pending update, when this process lived to see it fail. */
    suspend fun clearPending()

    /**
     * Forgets the pending update *and* remembers [versionCode] as the build the database now
     * matches, in one write — so a restart can never see one of the two without the other.
     */
    suspend fun settle(versionCode: Long)
}
