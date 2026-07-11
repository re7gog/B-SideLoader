package dev.re7gog.b_sideloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.local.entities.*

// TODO: Export schema for migration support
@Database(
    entities = [
        AppEntity::class,
        GithubDetailsEntity::class,
        TelegramDetailsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppsDatabase : RoomDatabase() {
    abstract fun appsDao(): AppsDao

    // Not released yet, so the schema stays at version 1 and dev builds recreate the DB on change
    // (fallbackToDestructiveMigration in AppsDatabaseModule). Once released, bump `version`, add a
    // Migration here, and register it in the builder instead of wiping data. Template:
    //
    // companion object {
    //     val MIGRATION_1_2 = object : Migration(1, 2) {
    //         override fun migrate(db: SupportSQLiteDatabase) {
    //             db.execSQL("ALTER TABLE apps ADD COLUMN advancedMode INTEGER NOT NULL DEFAULT 0")
    //         }
    //     }
    // }
    // (needs: androidx.room.migration.Migration, androidx.sqlite.db.SupportSQLiteDatabase)

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