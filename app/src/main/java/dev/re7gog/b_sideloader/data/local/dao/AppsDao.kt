package dev.re7gog.b_sideloader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entities.TelegramDetailsEntity
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface AppsDao {
    @Transaction // Because multiple queries inside
    @Query("SELECT * from apps ORDER BY name ASC")
    fun getAllApps(): Flow<List<AppWithDetails>>

    @Transaction
    @Query("SELECT * from apps WHERE id = :id")
    fun getApp(id: Long): Flow<AppWithDetails?>

    /** Finds an already-saved GitHub app by its source repo, if any. */
    @Transaction
    @Query(
        "SELECT apps.* FROM apps " +
            "INNER JOIN github_details ON apps.id = github_details.id " +
            "WHERE github_details.owner = :owner AND github_details.repo = :repo LIMIT 1"
    )
    suspend fun findGithubApp(owner: String, repo: String): AppWithDetails?

    /** Finds an already-saved Telegram app by its source chat/topic, if any. */
    @Transaction
    @Query(
        "SELECT apps.* FROM apps " +
            "INNER JOIN telegram_details ON apps.id = telegram_details.id " +
            "WHERE telegram_details.chatId = :chatId AND telegram_details.topicId = :topicId LIMIT 1"
    )
    suspend fun findTelegramApp(chatId: Long, topicId: Int): AppWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity): Long  // Returns id

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGithubDetails(details: GithubDetailsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelegramDetails(details: TelegramDetailsEntity)

    @Update
    suspend fun updateApp(app: AppEntity)

    @Update
    suspend fun updateGithubDetails(details: GithubDetailsEntity)

    @Update
    suspend fun updateTelegramDetails(details: TelegramDetailsEntity)

    @Delete
    suspend fun deleteApp(app: AppEntity)
}