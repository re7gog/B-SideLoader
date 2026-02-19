package dev.re7gog.b_sideloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity

// TODO: Export schema for migration support
@Database(
    entities = [
        AppEntity::class,
        GithubDetailsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppsDatabase : RoomDatabase() {
    abstract fun appsDao(): AppsDao

    // Not needed with Hilt
    /*
    companion object {
        @Volatile
        private var Instance: AppsDatabase? = null

        fun getDatabase(context: Context): AppsDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppsDatabase::class.java, "apps_database")
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
    */
}