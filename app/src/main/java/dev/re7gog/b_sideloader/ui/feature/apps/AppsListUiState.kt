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
) {
    val inSelectionMode: Boolean get() = selectedCount > 0
    val isEmpty: Boolean get() = !isLoading && apps.isEmpty()
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
)

/** Domain -> screen. Lives with the screen, because only the screen cares about this shape. */
fun TrackedAppStatus.toListItem(isSelected: Boolean): AppListItemUi = AppListItemUi(
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
)

fun List<TrackedAppStatus>.toListItems(selectedIds: Set<Long>): ImmutableList<AppListItemUi> =
    map { it.toListItem(isSelected = it.app.id in selectedIds) }.toImmutableList()
