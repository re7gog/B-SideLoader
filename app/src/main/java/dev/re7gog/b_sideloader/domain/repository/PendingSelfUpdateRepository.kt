package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate

/**
 * The one write-ahead record kept for an in-flight self-update.
 *
 * There is at most one: self-updates are installed one at a time, and a newer attempt supersedes
 * whatever the last one left behind.
 *
 * Unlike the other repositories, none of these throw
 * [dev.re7gog.b_sideloader.domain.error.AppError]: the record is a best-effort hint, and failing
 * an install because a preference could not be written would be a strictly worse outcome than
 * missing one version write.
 */
interface PendingSelfUpdateRepository {

    /** The record left by an install that has not been confirmed yet, or `null`. */
    suspend fun get(): PendingSelfUpdate?

    suspend fun put(pending: PendingSelfUpdate)

    suspend fun clear()
}
