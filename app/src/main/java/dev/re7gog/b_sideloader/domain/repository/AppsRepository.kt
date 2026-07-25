package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import kotlinx.coroutines.flow.Flow

/**
 * The set of apps the user tracks.
 *
 * Speaks only [TrackedApp]; the Room entities and the `@Relation` join that back it are an
 * implementation detail of `data/repository/RoomAppsRepository`.
 *
 * Suspending members throw [dev.re7gog.b_sideloader.domain.error.AppError] on failure. `Flow`
 * members never throw for expected conditions — a missing row is `null`, an empty table is an
 * empty list.
 */
interface AppsRepository {

    /** Every tracked app, ordered by name, re-emitted whenever the table changes. */
    fun observeApps(): Flow<List<TrackedApp>>

    /** One app by row id, or `null` once it is deleted. */
    fun observeApp(id: Long): Flow<TrackedApp?>

    /** Snapshot of every tracked app. Used by the background sweep, which must not subscribe. */
    suspend fun getApps(): List<TrackedApp>

    /** The saved app for [source], or `null` when this source is not tracked yet. */
    suspend fun findBySource(source: AppSource): TrackedApp?

    /** Inserts [app] and returns the assigned row id. */
    suspend fun add(app: TrackedApp): Long

    /** Persists changes to an already-saved app. No-op for an unsaved one. */
    suspend fun update(app: TrackedApp)

    /** Forgets the app. Does not touch the installed package. */
    suspend fun delete(app: TrackedApp)

    /** Forgets several apps in one transaction. */
    suspend fun deleteAll(apps: Collection<TrackedApp>)
}
