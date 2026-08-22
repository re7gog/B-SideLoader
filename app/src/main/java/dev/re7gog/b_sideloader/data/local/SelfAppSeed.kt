package dev.re7gog.b_sideloader.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.domain.model.SelfApp

/**
 * Puts B-SideLoader's own row into the apps table, so the app tracks and updates itself out of the
 * box instead of being the one thing on the phone it cannot keep current.
 *
 * Runs from two places, which between them cover every install: `onCreate` for a database created
 * at the current version, and the 1 -> 2 migration for a database that already exists. Both go
 * through raw SQL rather than the DAO because a `RoomDatabase.Callback` and a `Migration` are
 * handed a [SupportSQLiteDatabase] — the DAO does not exist yet at that point.
 *
 * The seeded version is deliberately *unknown* (an empty `version` column). A GitHub app's stored
 * version is the release *name*, and this project publishes releases as `v1.0.0` while the build
 * calls itself `1.0.0` — so there is no honest value to write here, and guessing one would make
 * the row claim to be up to date against a release it has never seen.
 *
 * Unknown reads as "not installed from here", which is both true and useful: the row offers an
 * install rather than an update, the background sweep leaves it alone until the user has run that
 * install once, and that first install is what makes B-SideLoader the installer of record for
 * itself — which is what buys silent updates from then on.
 *
 * Nothing here re-creates a deleted row: the seed is skipped when this repository is already
 * tracked, and it never runs again afterwards. A user who removes the row means it.
 */
internal object SelfAppSeed {

    fun insertInto(db: SupportSQLiteDatabase) {
        if (isTracked(db)) return

        val appId = db.insert(TABLE_APPS, SQLiteDatabase.CONFLICT_ABORT, appValues())
        // -1 means the insert was rolled back; writing details for a row that does not exist would
        // leave an orphan the mapper would then have to drop.
        if (appId == INSERT_FAILED) return
        db.insert(TABLE_GITHUB_DETAILS, SQLiteDatabase.CONFLICT_ABORT, githubValues(appId))
    }

    /** True when a row already points at this repository — a manually added one counts. */
    private fun isTracked(db: SupportSQLiteDatabase): Boolean = db.query(
        "SELECT 1 FROM $TABLE_GITHUB_DETAILS " +
            "WHERE owner = ? COLLATE NOCASE AND repo = ? COLLATE NOCASE LIMIT 1",
        arrayOf(SelfApp.OWNER, SelfApp.REPO),
    ).use { it.moveToFirst() }

    private fun appValues() = ContentValues().apply {
        put("sourceType", AppSourceKind.GitHub.storedValue)
        put("packageName", BuildConfig.APPLICATION_ID)
        put("name", SelfApp.NAME)
        // Unknown: see the class comment. AppVersion.Unknown is exactly this empty string.
        put("version", "")
        put("autoupdate", true)
        put("filterInclude", "")
        put("filterExclude", "")
        put("advancedMode", false)
    }

    private fun githubValues(appId: Long) = ContentValues().apply {
        put("id", appId)
        put("owner", SelfApp.OWNER)
        put("repo", SelfApp.REPO)
        put("usePrereleases", false)
        put("releasesInclude", "")
        put("releasesExclude", "")
    }

    private const val TABLE_APPS = "apps"
    private const val TABLE_GITHUB_DETAILS = "github_details"
    private const val INSERT_FAILED = -1L
}
