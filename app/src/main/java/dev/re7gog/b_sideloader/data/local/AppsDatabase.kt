package dev.re7gog.b_sideloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
        const val DB_VERSION = 1
        const val DB_NAME = "apps_database"

        /** Empty while the schema is still at its first version. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
