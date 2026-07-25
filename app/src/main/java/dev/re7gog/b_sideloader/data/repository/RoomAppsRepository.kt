package dev.re7gog.b_sideloader.data.repository

import androidx.room.withTransaction
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.data.local.AppsDatabase
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.mapper.toDomain
import dev.re7gog.b_sideloader.data.mapper.toDomainOrNull
import dev.re7gog.b_sideloader.data.mapper.toEntity
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [AppsRepository].
 *
 * Writes that touch two tables run inside one transaction, so an app row can never end up without
 * its details row (which the mapper would then have to drop).
 */
@Singleton
class RoomAppsRepository @Inject constructor(
    private val database: AppsDatabase,
    private val dao: AppsDao,
    private val dispatchers: DispatcherProvider,
) : AppsRepository {

    override fun observeApps(): Flow<List<TrackedApp>> =
        dao.observeAll().map { it.toDomain() }.flowOn(dispatchers.default)

    override fun observeApp(id: Long): Flow<TrackedApp?> =
        dao.observeById(id).map { it?.toDomainOrNull() }.flowOn(dispatchers.default)

    override suspend fun getApps(): List<TrackedApp> = withContext(dispatchers.io) {
        dao.getAll().toDomain()
    }

    override suspend fun findBySource(source: AppSource): TrackedApp? = withContext(dispatchers.io) {
        when (source) {
            is AppSource.GitHub -> dao.findGithubApp(source.owner, source.repo)
            is AppSource.Telegram -> dao.findTelegramApp(source.chatId, source.topicId)
        }?.toDomainOrNull()
    }

    override suspend fun add(app: TrackedApp): Long = withContext(dispatchers.io) {
        database.withTransaction {
            val id = dao.insertApp(app.toEntity())
            writeDetails(app, id)
            id
        }
    }

    override suspend fun update(app: TrackedApp) {
        if (!app.isSaved) return
        withContext(dispatchers.io) {
            database.withTransaction {
                dao.updateApp(app.toEntity())
                // Upsert rather than update: an app saved before its source gained a details row
                // (or restored from a partial backup) would otherwise silently keep stale filters.
                writeDetails(app, app.id)
            }
        }
    }

    override suspend fun delete(app: TrackedApp) = deleteAll(listOf(app))

    override suspend fun deleteAll(apps: Collection<TrackedApp>) {
        val ids = apps.filter { it.isSaved }.map { it.id }
        if (ids.isEmpty()) return
        withContext(dispatchers.io) { dao.deleteByIds(ids) }
    }

    private suspend fun writeDetails(app: TrackedApp, id: Long) {
        when (val source = app.source) {
            is AppSource.GitHub -> dao.upsertGithubDetails(source.toEntity(id))
            is AppSource.Telegram -> dao.upsertTelegramDetails(source.toEntity(id))
        }
    }
}
