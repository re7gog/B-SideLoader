package dev.re7gog.b_sideloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.re7gog.b_sideloader.data.local.dao.AppsDao
import dev.re7gog.b_sideloader.data.local.entity.AppEntity
import dev.re7gog.b_sideloader.data.local.entity.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entity.TelegramDetailsEntity

/**
 * The apps database.
 *
 * `exportSchema = true` writes `app/schemas/<version>.json` on every build. That file is checked
 * in and copied into the instrumented-test assets (see `app/build.gradle.kts`), which is what lets
 * `MigrationTest` open an old database and assert that the migration to the current version
 * actually succeeds — the previous setup exported nothing and relied on destructive fallback, so a
 * schema change would silently have wiped user data on the first release.
 *
 * ### Adding a schema change
 * 1. Bump [DB_VERSION].
 * 2. Add a `Migration(old, new)` to [MIGRATIONS].
 * 3. Build once so the new `schemas/<version>.json` is generated, and commit it.
 * 4. Add a case to `AppsDatabaseMigrationTest`.
 */
@Database(
    entities = [
        AppEntity::class,
        GithubDetailsEntity::class,
        TelegramDetailsEntity::class,
    ],
    version = AppsDatabase.DB_VERSION,
    exportSchema = true,
)
abstract class AppsDatabase : RoomDatabase() {
    abstract fun appsDao(): AppsDao

    companion object {
        const val DB_VERSION = 2
        const val DB_NAME = "apps_database"

        /**
         * Adds B-SideLoader's own row to a database created before it tracked itself.
         *
         * Data-only: the tables are byte-for-byte what version 1 declared, so there is no `ALTER`
         * here and `schemas/2.json` differs from `1.json` only in its version number. Room still
         * needs the migration to exist — without one it refuses to open the file at all.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = SelfAppSeed.insertInto(db)
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
