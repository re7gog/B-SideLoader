package dev.re7gog.b_sideloader.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.re7gog.b_sideloader.domain.model.SelfApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a real version-1 database and migrates it, which is the only way to find out whether a
 * migration works — Room validates the resulting schema against the exported `schemas/2.json`, so
 * a migration that drifts from the entities fails here rather than on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class AppsDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppsDatabase::class.java,
    )

    /** The 1 -> 2 migration exists solely to give an existing database the app's own row. */
    @Test
    fun migrate1To2_addsTheSelfRow() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertGithubApp(id = 1, name = "Other", owner = "octocat", repo = "example")
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, *AppsDatabase.MIGRATIONS)

        val names = migrated.githubAppNames()
        assertEquals(listOf("Other", SelfApp.NAME), names)
    }

    /** A user who added this repository by hand must not end up with it twice. */
    @Test
    fun migrate1To2_leavesAnAlreadyTrackedRepositoryAlone() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertGithubApp(id = 1, name = "Mine", owner = SelfApp.OWNER, repo = SelfApp.REPO)
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, *AppsDatabase.MIGRATIONS)

        assertEquals(listOf("Mine"), migrated.githubAppNames())
    }

    /** Casing is the user's choice; "RE7GOG/b-sideloader" is the same repository. */
    @Test
    fun migrate1To2_matchesAnExistingRowRegardlessOfCase() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertGithubApp(
                id = 1,
                name = "Mine",
                owner = SelfApp.OWNER.uppercase(),
                repo = SelfApp.REPO.lowercase(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, *AppsDatabase.MIGRATIONS)

        assertEquals(listOf("Mine"), migrated.githubAppNames())
    }

    /**
     * The seeded row has to be a well-formed GitHub app, or the mapper would silently drop it.
     *
     * Its version is empty on purpose — `AppVersion.Unknown`, meaning "never installed from this
     * source" — so the row offers an install instead of claiming to match a release it has never
     * seen. See `SelfAppSeed`.
     */
    @Test
    fun migrate1To2_seedsAUsableGithubRowWithAnUnknownVersion() {
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, *AppsDatabase.MIGRATIONS)

        migrated.query(
            "SELECT apps.packageName, apps.version, apps.autoupdate, " +
                "github_details.owner, github_details.repo " +
                "FROM apps INNER JOIN github_details ON apps.id = github_details.id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("dev.re7gog.b_sideloader", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(SelfApp.OWNER, cursor.getString(3))
            assertEquals(SelfApp.REPO, cursor.getString(4))
        }
    }

    private fun SupportSQLiteDatabase.insertGithubApp(
        id: Long,
        name: String,
        owner: String,
        repo: String,
    ) {
        execSQL(
            "INSERT INTO apps " +
                "(id, sourceType, packageName, name, version, autoupdate, filterInclude, " +
                "filterExclude, advancedMode) VALUES (?, 1, 'com.example', ?, '1.0', 1, '', '', 0)",
            arrayOf<Any>(id, name),
        )
        execSQL(
            "INSERT INTO github_details (id, owner, repo, usePrereleases, releasesInclude, " +
                "releasesExclude) VALUES (?, ?, ?, 0, '', '')",
            arrayOf<Any>(id, owner, repo),
        )
    }

    /** Names of every app that has a GitHub details row, in insertion order. */
    private fun SupportSQLiteDatabase.githubAppNames(): List<String> = query(
        "SELECT apps.name FROM apps INNER JOIN github_details ON apps.id = github_details.id " +
            "ORDER BY apps.id"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
