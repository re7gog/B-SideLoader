package dev.re7gog.b_sideloader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.re7gog.b_sideloader.data.entities.AppEntity
import dev.re7gog.b_sideloader.data.entities.AppWithDetails
import dev.re7gog.b_sideloader.data.entities.GithubDetailsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppsDao {
    @Transaction // Because multiple queries inside
    @Query("SELECT * from apps ORDER BY name ASC")
    fun getAllApps(): Flow<List<AppWithDetails>>

    @Transaction
    @Query("SELECT * from apps WHERE id = :id")
    fun getApp(id: Int): Flow<AppWithDetails>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: AppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGithubDetails(details: GithubDetailsEntity)

    @Update
    suspend fun updateApp(app: AppEntity)

    @Update
    suspend fun updateGithubDetails(details: GithubDetailsEntity)

    @Delete
    suspend fun deleteApp(app: AppEntity)
}