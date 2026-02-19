package dev.re7gog.b_sideloader.domain.repository

import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import kotlinx.coroutines.flow.Flow

interface AppsRepository {
    /**
     * Retrieve all the apps from the given data source.
     */
    fun getAllAppsStream(): Flow<List<AppWithDetails>>

    /**
     * Retrieve an app from the given data source that matches with the [id].
     */
    fun getAppStream(id: Long): Flow<AppWithDetails?>

    /**
     * Insert app in the data source
     */
    suspend fun addApp(app: AppType)

    /**
     * Update app in the data source
     */
    suspend fun updateApp(app: AppType)

    /**
     * Delete app from the data source
     */
    suspend fun deleteApp(app: AppEntity)
}