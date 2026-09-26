package dev.re7gog.b_sideloader.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.data.di.DatabaseModule
import dev.re7gog.b_sideloader.domain.model.SelfApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a real version-1 database and migrates it, which is the only way to find out whether a
 * migration works.
 *
 * The old database is built from the exported `schemas/1.json` ([ExportedSchema]) and then opened
 * through the production builder in [DatabaseModule], so the migrations under test are exactly the
 * ones that ship. Room validates the migrated tables against the entities as it opens, so a
 * migration that drifts from them fails here rather than on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class AppsDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: AppsDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    /** The 1 -> 2 migration exists solely to give an existing database the app's own row. */
    @Test
    fun migrate1To2_addsTheSelfRow() {
        createVersion1 { insertGithubApp(id = 1, name = "Other", owner = "octocat", repo = "example") }

        assertEquals(listOf("Other", SelfApp.NAME), migrate().githubAppNames())
    }

    /** A user who added this repository by hand must not end up with it twice. */
    @Test
    fun migrate1To2_leavesAnAlreadyTrackedRepositoryAlone() {
        createVersion1 { insertGithubApp(id = 1, name = "Mine", owner = SelfApp.OWNER, repo = SelfApp.REPO) }

        assertEquals(listOf("Mine"), migrate().githubAppNames())
    }

    /** Casing is the user's choice; "RE7GOG/b-sideloader" is the same repository. */
    @Test
    fun migrate1To2_matchesAnExistingRowRegardlessOfCase() {
        createVersion1 {
            insertGithubApp(
                id = 1,
                name = "Mine",
                owner = SelfApp.OWNER.uppercase(),
                repo = SelfApp.REPO.lowercase(),
            )
        }

        assertEquals(listOf("Mine"), migrate().githubAppNames())
    }

    /**
     * The seeded row has to be a well-formed GitHub app, or the mapper would silently drop it.
     *
     * Its version is the release this build is — the tag CI built it from, or empty
     * (`AppVersion.Unknown`) for an untagged local build. See `SelfAppSeed`.
     */
    @Test
    fun migrate1To2_seedsAUsableGithubRowWithThisBuildsRelease() {
        createVersion1()

        migrate().query(
            "SELECT apps.packageName, apps.version, apps.autoupdate, " +
                "github_details.owner, github_details.repo " +
                "FROM apps INNER JOIN github_details ON apps.id = github_details.id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(BuildConfig.APPLICATION_ID, cursor.getString(0))
            assertEquals(BuildConfig.RELEASE_TAG, cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(SelfApp.OWNER, cursor.getString(3))
            assertEquals(SelfApp.REPO, cursor.getString(4))
        }
    }

    /** Existing apps keep every column across the migration — it is data-only. */
    @Test
    fun migrate1To2_keepsExistingRowsIntact() {
        createVersion1 { insertGithubApp(id = 7, name = "Other", owner = "octocat", repo = "example") }

        val migrated = migrate()

        migrated.query("SELECT id, packageName, version FROM apps WHERE name = 'Other'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals("com.example", cursor.getString(1))
            assertEquals("1.0", cursor.getString(2))
        }
    }

    private fun createVersion1(populate: SupportSQLiteDatabase.() -> Unit = {}) =
        ExportedSchema.load(version = 1).createDatabase(context, AppsDatabase.DB_NAME, populate)

    /** Opens the database the way the app does, which runs every pending migration. */
    private fun migrate(): SupportSQLiteDatabase {
        val opened = DatabaseModule.provideAppsDatabase(context)
        database = opened
        return opened.openHelper.writableDatabase
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
}
