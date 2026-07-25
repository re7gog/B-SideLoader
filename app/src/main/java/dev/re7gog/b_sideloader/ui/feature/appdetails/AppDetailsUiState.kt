package dev.re7gog.b_sideloader.ui.feature.appdetails

import androidx.compose.runtime.Immutable
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.model.UpdateStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    val candidates: ImmutableList<UpdateCandidate> = persistentListOf(),
    /** The candidate that would actually be installed. */
    val target: UpdateCandidate? = null,
    val install: InstallProgress? = null,
    /** Set once this screen's own install succeeded; drives where back goes. */
    val installSucceeded: Boolean = false,
) {
    val isSaved: Boolean get() = app?.isSaved == true
    val isInstalling: Boolean get() = install != null
    val isTelegram: Boolean get() = app?.source is AppSource.Telegram
    val isGithub: Boolean get() = app?.source is AppSource.GitHub

    /**
     * The single primary button. Order matters: an app opened from search always installs on the
     * first tap (its edits are persisted as part of that install).
     */
    val primaryAction: PrimaryAction
        get() = when {
            !isSaved -> PrimaryAction.SaveAndInstall
            hasUnsavedChanges -> PrimaryAction.SaveChanges
            updateStatus == UpdateStatus.UpdateAvailable -> PrimaryAction.Update
            !isInstalled -> PrimaryAction.Install
            else -> PrimaryAction.Open
        }

    /** Actions that download an APK need a resolved target and a settled lookup. */
    val isPrimaryEnabled: Boolean
        get() = when (primaryAction) {
            PrimaryAction.SaveChanges, PrimaryAction.Open -> true
            PrimaryAction.SaveAndInstall, PrimaryAction.Update, PrimaryAction.Install ->
                target != null && !isResolving
        }
}

enum class PrimaryAction { SaveAndInstall, SaveChanges, Update, Install, Open }

/** The source-specific header. */
@Immutable
sealed interface HeadlineUi {
    val title: String

    @Immutable
    data class GitHub(
        override val title: String,
        val owner: String,
        val description: String? = null,
        val stars: Int = 0,
        val avatarUrl: String? = null,
    ) : HeadlineUi

    @Immutable
    data class Telegram(
        override val title: String,
        val photoFileId: Int? = null,
    ) : HeadlineUi
}
