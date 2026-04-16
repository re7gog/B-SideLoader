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