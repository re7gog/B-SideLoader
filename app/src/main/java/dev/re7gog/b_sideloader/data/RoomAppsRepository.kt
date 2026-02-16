package dev.re7gog.b_sideloader.data

import dev.re7gog.b_sideloader.data.entities.AppEntity
import dev.re7gog.b_sideloader.data.entities.AppWithDetails
import kotlinx.coroutines.flow.Flow

class RoomAppsRepository(private val appsDao: AppsDao) : AppsRepository {
    override fun getAllAppsStream(): Flow<List<AppWithDetails>> = appsDao.getAllApps()

    override fun getAppStream(id: Int): Flow<AppWithDetails?> = appsDao.getApp(id)

    override suspend fun addApp(app: AppType) {
        when (app) {
            is AppType.GithubApp -> {
                appsDao.insertApp(app.app)
                appsDao.insertGithubDetails(app.details)
            }
        }
    }

    override suspend fun updateApp(app: AppType) {
        when (app) {
            is AppType.GithubApp -> {
                appsDao.updateApp(app.app)
                appsDao.updateGithubDetails(app.details)
            }
        }
    }

    override suspend fun deleteApp(app: AppEntity) {
        appsDao.deleteApp(app)
    }
}