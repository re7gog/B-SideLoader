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
     * Find an already-saved GitHub app by its source [owner]/[repo], or null if not saved.
     */
    suspend fun findGithubApp(owner: String, repo: String): AppWithDetails?

    /**
     * Find an already-saved Telegram app by its source [chatId]/[topicId], or null if not saved.
     */
    suspend fun findTelegramApp(chatId: Long, topicId: Int): AppWithDetails?

    /**
     * Insert app in the data source, returning the new app id.
     */
    suspend fun addApp(app: AppType): Long

    /**
     * Update app in the data source
     */
    suspend fun updateApp(app: AppType)

    /**
     * Delete app from the data source
     */
    suspend fun deleteApp(app: AppEntity)
}