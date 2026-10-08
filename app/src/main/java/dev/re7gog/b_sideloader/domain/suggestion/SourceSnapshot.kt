package dev.re7gog.b_sideloader.domain.suggestion

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.selection.GithubApkSelector
import dev.re7gog.b_sideloader.domain.selection.TelegramApkSelector

/**
 * What a source offered when the user asked for a suggestion, before any filter: the one data set
 * every proposal is checked against, so that checking it costs no further requests.
 */
sealed interface SourceSnapshot {
    data class GitHub(val releases: List<GithubRelease>) : SourceSnapshot
    data class Telegram(val documents: List<TelegramApkDocument>) : SourceSnapshot
}

/**
 * The groups [app]'s filters accept, exactly as the details page and the update check see them —
 * the very selectors they call. Empty when the snapshot is of another source kind than the app.
 */
fun SourceSnapshot.groups(app: TrackedApp): List<CandidateGroup> {
    val source = app.source
    return when {
        this is SourceSnapshot.GitHub && source is AppSource.GitHub ->
            GithubApkSelector.groups(releases, app, source)

        this is SourceSnapshot.Telegram && source is AppSource.Telegram ->
            TelegramApkSelector.groups(documents, app, source)

        else -> emptyList()
    }
}

/**
 * Every release or message that carries an APK, newest first, ignoring [app]'s filters: what the
 * example picker offers and what a language model is shown.
 */
fun SourceSnapshot.samples(app: TrackedApp): List<SampleGroup> = when (this) {
    is SourceSnapshot.GitHub -> releases.mapNotNull { release ->
        val files = release.assets.filter { it.isApk }.map { SampleFile(it.name, DownloadRef.Http(it.downloadUrl)) }
        if (files.isEmpty()) return@mapNotNull null
        SampleGroup(
            label = release.name,
            filterTarget = release.name,
            isPrerelease = release.isPrerelease,
            files = files,
        )
    }

    is SourceSnapshot.Telegram -> {
        // The selector is what stitches albums back together; with no filters it keeps them all.
        val unfiltered = app.copy(assetFilter = FilterRule.None).let { draft ->
            val source = draft.source as? AppSource.Telegram ?: return emptyList()
            draft.copy(source = source.copy(messageFilter = FilterRule.None))
        }
        TelegramApkSelector.groups(documents, unfiltered, unfiltered.source as AppSource.Telegram).map { group ->
            val caption = group.notes.orEmpty()
            SampleGroup(
                label = caption.lineSequence().firstOrNull { it.isNotBlank() }?.trim(),
                filterTarget = caption,
                isPrerelease = false,
                files = group.files.map { SampleFile(it.candidate.fileName, it.candidate.download) },
            )
        }
    }
}

/** One release or message, unfiltered. */
data class SampleGroup(
    /** The release name, or the first line of a message's caption. */
    val label: String?,
    /** What the release or message filter is matched against: the release name or the whole caption. */
    val filterTarget: String,
    val isPrerelease: Boolean,
    /** Every APK in it, in the order the source lists them. */
    val files: List<SampleFile>,
)

/** One APK of a [SampleGroup]. [ref] tells it apart from same-named files in other groups. */
data class SampleFile(
    val name: String,
    val ref: DownloadRef,
)
