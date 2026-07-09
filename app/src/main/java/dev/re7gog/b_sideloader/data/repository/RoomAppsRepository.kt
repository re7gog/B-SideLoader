package dev.re7gog.b_sideloader.data.repository

import androidx.room.withTransaction
import dev.re7gog.b_sideloader.data.local.AppsDatabase
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomAppsRepository @Inject constructor(
    private val appsDatabase: AppsDatabase,
    private val appsDao: AppsDao
) : AppsRepository {
    override fun getAllAppsStream(): Flow<List<AppWithDetails>> = appsDao.getAllApps()
    override fun getAppStream(id: Long): Flow<AppWithDetails?> = appsDao.getApp(id)

    override suspend fun findGithubApp(owner: String, repo: String): AppWithDetails? =
        appsDao.findGithubApp(owner, repo)

    override suspend fun findTelegramApp(chatId: Long, topicId: Int): AppWithDetails? =
        appsDao.findTelegramApp(chatId, topicId)

    override suspend fun addApp(app: AppType): Long {
        return appsDatabase.withTransaction {  // Atomic transaction
            when (app) {
                is AppType.GithubApp -> {
                    val id = appsDao.insertApp(app.app)
                    appsDao.insertGithubDetails(app.details.copy(id = id))
                    id
                }
                is AppType.TelegramApp -> {
                    val id = appsDao.insertApp(app.app)
                    appsDao.insertTelegramDetails(app.details.copy(id = id))
                    id
                }
            }
        }
    }

    override suspend fun updateApp(app: AppType) {
        appsDatabase.withTransaction {
            when (app) {
                is AppType.GithubApp -> {
                    appsDao.updateApp(app.app)
                    appsDao.updateGithubDetails(app.details)
                }
                is AppType.TelegramApp -> {
                    appsDao.updateApp(app.app)
                    appsDao.updateTelegramDetails(app.details)
                }
            }
        }
    }

    override suspend fun deleteApp(app: AppEntity) {
        appsDao.deleteApp(app)  // No need to delete details because of ForeignKey(onDelete = CASCADE)
    }
}