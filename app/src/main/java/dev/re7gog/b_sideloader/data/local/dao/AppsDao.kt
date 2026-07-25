package dev.re7gog.b_sideloader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.re7gog.b_sideloader.data.local.entity.AppEntity
import dev.re7gog.b_sideloader.data.local.entity.AppWithDetails
import dev.re7gog.b_sideloader.data.local.entity.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entity.TelegramDetailsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppsDao {

    @Transaction // multiple queries under the hood (@Relation)
    @Query("SELECT * FROM apps ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppWithDetails>>

    @Transaction
    @Query("SELECT * FROM apps ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<AppWithDetails>

    @Transaction
    @Query("SELECT * FROM apps WHERE id = :id")
    fun observeById(id: Long): Flow<AppWithDetails?>

    @Transaction
    @Query(
        "SELECT apps.* FROM apps " +
            "INNER JOIN github_details ON apps.id = github_details.id " +
            "WHERE github_details.owner = :owner COLLATE NOCASE " +
            "AND github_details.repo = :repo COLLATE NOCASE LIMIT 1"
    )
    suspend fun findGithubApp(owner: String, repo: String): AppWithDetails?

    @Transaction
    @Query(
        "SELECT apps.* FROM apps " +
            "INNER JOIN telegram_details ON apps.id = telegram_details.id " +
            "WHERE telegram_details.chatId = :chatId AND telegram_details.topicId = :topicId LIMIT 1"
    )
    suspend fun findTelegramApp(chatId: Long, topicId: Int): AppWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGithubDetails(details: GithubDetailsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTelegramDetails(details: TelegramDetailsEntity)

    @Update
    suspend fun updateApp(app: AppEntity)

    /** Details rows cascade-delete with their app row. */
    @Query("DELETE FROM apps WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
