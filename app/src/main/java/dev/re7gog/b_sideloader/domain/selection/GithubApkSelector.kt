package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate

/**
 * Turns a repository's releases into the one APK this device should install.
 *
 * Pure function of its inputs, so the details screen can re-run it on every keystroke to preview
 * what the current filters select, and the background updater can run the exact same code.
 */
object GithubApkSelector {

    /**
     * Walks releases newest-first and returns the first APK that passes every filter and can run
     * on [deviceAbis], or `null` when nothing qualifies.
     */
    fun select(
        releases: List<GithubRelease>,
        app: TrackedApp,
        source: AppSource.GitHub,
        deviceAbis: List<String>,
    ): UpdateCandidate? {
        for (release in releases) {
            if (release.isPrerelease && !source.usePrereleases) continue
            if (!NameMatcher.matches(release.name, source.releaseFilter, app.filterMode)) continue

            val assets = release.assets.filter { asset ->
                asset.isApk && NameMatcher.matches(asset.name, app.assetFilter, app.filterMode)
            }
            if (assets.isEmpty()) continue

            // Prefer an APK that can actually run here (matching split or universal) over merely
            // the first one listed; only fall back to the first if nothing is installable.
            val target = AbiMatcher.pickInstallable(assets, deviceAbis) { it.name } ?: continue
            return UpdateCandidate(
                version = AppVersion(release.name),
                download = DownloadRef.Http(target.downloadUrl),
                fileName = target.name,
                sizeBytes = target.sizeBytes.takeIf { it > 0 },
                notes = release.notes.takeIf { it.isNotBlank() },
            )
        }
        return null
    }

    /**
     * Every APK the current filters accept, newest first — what the details screen lists so the
     * user can see why a particular file was chosen.
     */
    fun matchingAssets(
        releases: List<GithubRelease>,
        app: TrackedApp,
        source: AppSource.GitHub,
    ): List<UpdateCandidate> = releases
        .asSequence()
        .filter { !it.isPrerelease || source.usePrereleases }
        .filter { NameMatcher.matches(it.name, source.releaseFilter, app.filterMode) }
        .flatMap { release ->
            release.assets.asSequence()
                .filter { it.isApk && NameMatcher.matches(it.name, app.assetFilter, app.filterMode) }
                .map { asset ->
                    UpdateCandidate(
                        version = AppVersion(release.name),
                        download = DownloadRef.Http(asset.downloadUrl),
                        fileName = asset.name,
                        sizeBytes = asset.sizeBytes.takeIf { size -> size > 0 },
                        notes = release.notes.takeIf { notes -> notes.isNotBlank() },
                    )
                }
        }
        .toList()
}
