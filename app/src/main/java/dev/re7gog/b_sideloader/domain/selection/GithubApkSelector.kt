package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.CandidateFile
import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate

/**
 * Turns a repository's releases into candidate groups, and from those the one APK to install.
 *
 * Pure function of its inputs, so the details screen can re-run it on every keystroke to preview
 * what the current filters select, and the background updater can run the exact same code.
 */
object GithubApkSelector {

    /**
     * One group per release the release filter (and the prerelease switch) accepts, newest first,
     * holding every APK asset of it — skipping releases where the APK filter accepts none. What
     * the details screen lists, so the user can see each release whole and why a file was chosen.
     */
    fun groups(
        releases: List<GithubRelease>,
        app: TrackedApp,
        source: AppSource.GitHub,
    ): List<CandidateGroup> = releases
        .asSequence()
        .filter { !it.isPrerelease || source.usePrereleases }
        .filter { NameMatcher.matches(it.name, source.releaseFilter, app.filterMode) }
        .mapNotNull { release ->
            val notes = release.notes.takeIf { it.isNotBlank() }
            val files = release.assets.filter { it.isApk }.map { asset ->
                CandidateFile(
                    candidate = UpdateCandidate(
                        version = AppVersion(release.name),
                        download = DownloadRef.Http(asset.downloadUrl),
                        fileName = asset.name,
                        sizeBytes = asset.sizeBytes.takeIf { it > 0 },
                        notes = notes,
                    ),
                    matchesFilter = NameMatcher.matches(asset.name, app.assetFilter, app.filterMode),
                )
            }
            CandidateGroup(title = release.name, notes = notes, files = files)
                .takeIf { group -> group.files.any { it.matchesFilter } }
        }
        .toList()

    /**
     * The APK to install, or `null` when nothing qualifies: see [TargetSelector] — the newest
     * release with a file this device can run, and the best such file in it.
     */
    fun select(
        releases: List<GithubRelease>,
        app: TrackedApp,
        source: AppSource.GitHub,
        deviceAbis: List<String>,
    ): UpdateCandidate? = TargetSelector.select(groups(releases, app, source), deviceAbis)
}
