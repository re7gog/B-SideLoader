package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.compose.runtime.Immutable
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.domain.usecase.TrackedAppStatus
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * What the apps list renders. Screen-shaped, not domain-shaped: it carries the selection flag and
 * a ready-to-show subtitle, so the composable does no branching on [AppSource] at all.
 *
 * [Immutable] plus `ImmutableList` so Compose can skip recomposition of unchanged rows — a plain
 * `List` is treated as unstable and would re-run every item on every state change.
 */
@Immutable
data class AppsListUiState(
    val apps: ImmutableList<AppListItemUi> = persistentListOf(),
    val isLoading: Boolean = true,
    val selectedCount: Int = 0,
    /** True while a check is in flight; drives the pull-to-refresh spinner. */
    val isRefreshing: Boolean = false,
    /** Shown once, on the first list the user ever sees, and dismissed for good after that. */
    val showLongPressHint: Boolean = false,
) {
    val inSelectionMode: Boolean get() = selectedCount > 0
    val isEmpty: Boolean get() = !isLoading && apps.isEmpty()

    val updatableCount: Int get() = apps.count { it.canUpdate }

    /** "Update all" is pointless mid-update and during selection, so the bar hides it then. */
    val showUpdateAll: Boolean get() = !inSelectionMode && updatableCount > 0
}

/**
 * Where one row stands relative to its source.
 *
 * There is no "checking" value on purpose: a refresh keeps the previous verdicts until the new
 * ones arrive, so the per-row buttons do not flicker away on every pull. The in-flight check is
 * reported once, by [AppsListUiState.isRefreshing].
 */
enum class AppUpdateState {
    /** Not checked yet, or deliberately not checked because the app is not installed. */
    Unknown,

    UpToDate,
    Available,
    Updating,

    /** The last check failed. The row stays usable; the error went to the snackbar. */
    Failed,
}

@Immutable
data class AppListItemUi(
    val id: Long,
    val name: String,
    val packageName: String,
    val subtitle: UiText?,
    val isInstalled: Boolean,
    val isSelected: Boolean,
    val sourceKind: AppSourceKind,
    val updateState: AppUpdateState = AppUpdateState.Unknown,
    /** 0f..1f while this row is installing; `null` when the phase has no measurable progress. */
    val updateProgress: Float? = null,
) {
    val canUpdate: Boolean get() = updateState == AppUpdateState.Available
    val isUpdating: Boolean get() = updateState == AppUpdateState.Updating

    /**
     * Ordering rank. Apps with something to do come first, apps that are not on the device sink to
     * the bottom — they cannot be updated and are the least likely thing the user opened the list
     * for. Everything else keeps the repository's alphabetical order, which survives because
     * `sortedBy` is stable.
     */
    val sortRank: Int
        get() = when {
            !isInstalled -> RANK_NOT_INSTALLED
            canUpdate || isUpdating -> RANK_UPDATABLE
            else -> RANK_INSTALLED
        }

    private companion object {
        const val RANK_UPDATABLE = 0
        const val RANK_INSTALLED = 1
        const val RANK_NOT_INSTALLED = 2
    }
}

/** Domain -> screen. Lives with the screen, because only the screen cares about this shape. */
fun TrackedAppStatus.toListItem(
    isSelected: Boolean,
    updateState: AppUpdateState = AppUpdateState.Unknown,
    updateProgress: Float? = null,
): AppListItemUi = AppListItemUi(
    id = app.id,
    name = app.name,
    packageName = app.packageName,
    subtitle = when (val source = app.source) {
        is AppSource.GitHub -> UiText.of(R.string.by_owner, source.owner)
        is AppSource.Telegram -> UiText.of(R.string.telegram_channel)
    },
    isInstalled = isInstalled,
    isSelected = isSelected,
    sourceKind = app.source.kind,
    updateState = updateState,
    updateProgress = updateProgress,
)

fun List<TrackedAppStatus>.toListItems(
    selectedIds: Set<Long>,
    updateStates: Map<Long, AppUpdateState> = emptyMap(),
    updateProgress: Map<Long, Float?> = emptyMap(),
): ImmutableList<AppListItemUi> = map { status ->
    status.toListItem(
        isSelected = status.app.id in selectedIds,
        // An app that is not on the device is never checked, so it has no meaningful state.
        updateState = if (status.isInstalled) {
            updateStates[status.app.id] ?: AppUpdateState.Unknown
        } else {
            AppUpdateState.Unknown
        },
        updateProgress = updateProgress[status.app.id],
    )
}.sortedBy { it.sortRank }.toImmutableList()
