package dev.re7gog.b_sideloader.ui.feature.appdetails

import androidx.compose.runtime.Immutable
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.CandidateGroup
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.model.UpdateStatus
import dev.re7gog.b_sideloader.domain.selection.AbiMatcher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * How the details screen was opened.
 *
 * A plain (non-`NavKey`) type so the ViewModel does not depend on the navigation library, and so
 * the same ViewModel can be driven from a test with a literal.
 */
sealed interface AppDetailsArgs {
    /** An app already in the database. */
    data class Saved(val appId: Long) : AppDetailsArgs

    data class NewGithub(
        val owner: String,
        val repo: String,
        val name: String,
        val description: String? = null,
        val stars: Int = 0,
        val avatarUrl: String? = null,
    ) : AppDetailsArgs

    data class NewTelegram(
        val chatId: Long,
        val topicId: Int,
        val title: String,
    ) : AppDetailsArgs
}

/**
 * One state for both sources.
 *
 * The GitHub and Telegram detail pages used to be two screens, two ViewModels and two UI states
 * that were ~90% the same code with independently drifting bugs. What actually differs between
 * them is the header and which filter fields exist — [headline] and [app]`.source` — so this is
 * one state with a source-shaped part.
 */
@Immutable
data class AppDetailsUiState(
    val isLoading: Boolean = true,
    /** The working copy, including edits the user has not saved yet. */
    val app: TrackedApp? = null,
    val headline: HeadlineUi? = null,
    val isInstalled: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.NoCandidate,
    /** True while a source lookup is in flight; installs are disabled meanwhile. */
    val isResolving: Boolean = false,
    /** Every release or message the filters accept, newest first, each with all its APKs. */
    val apkGroups: ImmutableList<ApkGroupUi> = persistentListOf(),
    /** The candidate that would actually be installed. */
    val target: UpdateCandidate? = null,
    val install: InstallProgress? = null,
    /**
     * Opened from search for a source that is already tracked. Changes nothing but the label of
     * the first install: the page is otherwise the same as for a source seen for the first time.
     */
    val alreadyAdded: Boolean = false,
) {
    val isSaved: Boolean get() = app?.isSaved == true
    val isInstalling: Boolean get() = install != null
    val isTelegram: Boolean get() = app?.source is AppSource.Telegram
    val isGithub: Boolean get() = app?.source is AppSource.GitHub

    /**
     * The name is editable, so it can be emptied. A nameless row in the apps list is unusable, so
     * the state reports it rather than letting a blank one be saved and dealt with later.
     */
    val isNameValid: Boolean get() = app?.name?.isNotBlank() == true

    /**
     * The single primary button. Order matters: an app opened from search always installs on the
     * first tap (its edits are persisted as part of that install). When the source is already
     * tracked, that same first install is labelled [PrimaryAction.AddAgain] instead.
     */
    val primaryAction: PrimaryAction
        get() = when {
            !isSaved -> if (alreadyAdded) PrimaryAction.AddAgain else PrimaryAction.SaveAndInstall
            hasUnsavedChanges -> PrimaryAction.SaveChanges
            updateStatus == UpdateStatus.UpdateAvailable -> PrimaryAction.Update
            !isInstalled -> PrimaryAction.Install
            else -> PrimaryAction.Open
        }

    /** Actions that download an APK need a resolved target and a settled lookup. */
    val isPrimaryEnabled: Boolean
        get() = isNameValid && when (primaryAction) {
            PrimaryAction.SaveChanges, PrimaryAction.Open -> true
            PrimaryAction.SaveAndInstall, PrimaryAction.AddAgain, PrimaryAction.Update,
            PrimaryAction.Install,
            -> target != null && !isResolving
        }
}

enum class PrimaryAction { SaveAndInstall, SaveChanges, Update, Install, Open, AddAgain }

/**
 * The source-specific part of the header: the avatar and whatever only that source can say.
 *
 * Deliberately carries no title. The name is editable now, so it lives in exactly one place —
 * [AppDetailsUiState.app]`.name` — and a copy here would go stale the moment the user renamed the
 * app, leaving the heading showing the old name.
 */
@Immutable
sealed interface HeadlineUi {

    @Immutable
    data class GitHub(
        val owner: String,
        val description: String? = null,
        val stars: Int = 0,
        val avatarUrl: String? = null,
    ) : HeadlineUi

    @Immutable
    data class Telegram(val photoFileId: Int? = null) : HeadlineUi
}

/**
 * One GitHub release or Telegram message on the details page, shown whole: its name and notes once,
 * then every APK in it, so the user sees why one file was picked over its siblings.
 */
@Immutable
data class ApkGroupUi(
    /** The release name. Telegram messages have none. */
    val title: String?,
    val notes: String?,
    val files: ImmutableList<ApkFileUi>,
) {
    /**
     * Stable across re-resolves, since no two groups share a file. A string because lazy-list keys
     * are saved in a `Bundle`.
     */
    val key: String get() = files.first().candidate.download.toString()

    val containsTarget: Boolean get() = files.any { it.isTarget }
}

@Immutable
data class ApkFileUi(
    val candidate: UpdateCandidate,
    /** The file an install or update would use. At most one in the whole list. */
    val isTarget: Boolean,
    /** False when the APK filter leaves it out: listed for context, never installed. */
    val matchesFilter: Boolean,
    /** False when it is built only for architectures this device lacks. */
    val runsOnDevice: Boolean,
)

/** Domain -> screen, marking [target] and what this device can run. */
fun List<CandidateGroup>.toApkGroupsUi(
    target: UpdateCandidate?,
    deviceAbis: List<String>,
): ImmutableList<ApkGroupUi> = map { group ->
    ApkGroupUi(
        title = group.title,
        notes = group.notes,
        files = group.files.map { file ->
            ApkFileUi(
                candidate = file.candidate,
                isTarget = file.candidate == target,
                matchesFilter = file.matchesFilter,
                runsOnDevice = AbiMatcher.runsOn(file.candidate.fileName, deviceAbis),
            )
        }.toImmutableList(),
    )
}.toImmutableList()
