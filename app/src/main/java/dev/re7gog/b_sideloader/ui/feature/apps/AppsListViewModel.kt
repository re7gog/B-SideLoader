package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.usecase.CheckUpdatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallCoordinator
import dev.re7gog.b_sideloader.domain.usecase.InstallKey
import dev.re7gog.b_sideloader.domain.usecase.InstallResult
import dev.re7gog.b_sideloader.domain.usecase.ObserveTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.ReconcileSelfUpdateUseCase
import dev.re7gog.b_sideloader.domain.usecase.TrackedAppStatus
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The apps list.
 *
 * Holds selection state and the result of the last update check; everything else is derived from
 * the database and live package changes. Notably it does not call `PackageManager` from the
 * composable, nor keep a hand-incremented "refresh key" to notice installs —
 * [ObserveTrackedAppsUseCase] pushes both.
 *
 * A check runs once when the list first appears and again on every pull-to-refresh. Which apps get
 * queried is [CheckUpdatesUseCase]'s decision, not this class's, so the list and the background
 * sweep cannot end up asking different questions.
 *
 * Installs are not owned here either: they go through [InstallCoordinator], which every screen
 * reads, so a row shows the progress of an install started from the app's details page and the
 * page shows one started from here.
 */
@HiltViewModel
class AppsListViewModel @Inject constructor(
    observeTrackedApps: ObserveTrackedAppsUseCase,
    private val appsRepository: AppsRepository,
    private val checkUpdates: CheckUpdatesUseCase,
    private val installCoordinator: InstallCoordinator,
    private val reconcileSelfUpdate: ReconcileSelfUpdateUseCase,
    private val deleteTrackedApps: DeleteTrackedAppsUseCase,
    private val uninstallApps: UninstallAppsUseCase,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val updates = MutableStateFlow(UpdateBoard())

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    /** The apps as last observed, for acting on a selection without re-querying. */
    private var lastKnownApps: List<TrackedAppStatus> = emptyList()

    private var checkJob: Job? = null

    private val longPressHintSeen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.longPressHintSeen }
        .distinctUntilChanged()
        // Assume seen until the store answers, so the hint cannot flash on every cold start.
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val uiState: StateFlow<AppsListUiState> = combine(
        observeTrackedApps().catch { throwable ->
            logger.e(TAG, throwable) { "Apps list stream failed" }
            emit(emptyList())
        },
        selectedIds,
        updates,
        longPressHintSeen,
        installCoordinator.installs.map { it.byAppId() }.distinctUntilChanged(),
    ) { apps, selected, board, hintSeen, installing ->
        lastKnownApps = apps
        // Drop ids of apps that disappeared, otherwise the selection count outlives the rows.
        val liveSelection = selected intersect apps.mapTo(mutableSetOf()) { it.app.id }
        val items = apps.toListItems(liveSelection, board.statesFor(apps), installing)
        AppsListUiState(
            apps = items,
            isLoading = false,
            selectedCount = liveSelection.size,
            isRefreshing = board.isChecking,
            showLongPressHint = !hintSeen && items.size > 1 && liveSelection.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppsListUiState(),
    )

    init {
        // "Check on open": this ViewModel is created when the apps tab is first composed, which is
        // the app's first screen.
        refresh()
        observeInstallResults()
    }

    // ---- update checking --------------------------------------------------------------------

    /**
     * Re-checks every installed app. A second call while one is running is ignored.
     *
     * The app list comes from a repository snapshot rather than from [lastKnownApps]: this is
     * called from `init`, before anything has subscribed to [uiState], so the observed list has
     * not arrived yet and the check would silently have nothing to do.
     *
     * The snapshot is taken only once B-SideLoader's own row is reconciled. Right after a
     * self-update this is the first screen, and a snapshot taken a moment earlier would still hold
     * the old version — showing the update that was just installed.
     */
    fun refresh() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            reconcileSelfUpdate()
            val apps = appsRepository.getApps()
            // Deliberately does not reset the per-row verdicts: the refresh indicator already says
            // a check is running, and blanking them would make every "Update" button disappear and
            // reappear on each pull, which reads as the list losing its place.
            updates.update { it.copy(isChecking = true) }
            try {
                val outcomes = checkUpdates(apps)
                val firstError = outcomes.firstNotNullOfOrNull { it.error }
                updates.update { board -> board.withCheckResults(outcomes) }
                firstError?.let { _messages.tryEmit(it.toUiText()) }
            } finally {
                updates.update { it.copy(isChecking = false) }
            }
        }
    }

    // ---- installing -------------------------------------------------------------------------

    /** Installs the candidate found for one app by the last check. */
    fun updateApp(id: Long) = install(listOf(id))

    /** Installs every app the last check found an update for; they run one at a time. */
    fun updateAll() = install(uiState.value.apps.filter { it.canUpdate }.map { it.id })

    /**
     * Hands the installs to [InstallCoordinator], which runs them one after another and ignores an
     * app that is already queued — so a second "Update all", or "Update" here after starting the
     * same app on its page, does not install anything twice.
     */
    private fun install(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val apps = appsRepository.getApps().associateBy { it.id }
            ids.forEach { id ->
                val app = apps[id] ?: return@forEach
                val candidate = updates.value.candidates[id] ?: return@forEach
                installCoordinator.install(app, candidate)
            }
        }
    }

    /**
     * Results of every saved app's install, not just the ones started here: an install from the
     * details page that finishes must settle this row too. A failure is reported whoever started
     * it — this snackbar only shows while the list is on screen, and then the list is where the
     * user is looking.
     */
    private fun observeInstallResults() {
        viewModelScope.launch {
            installCoordinator.results.collect { result ->
                val id = (result.key as? InstallKey.App)?.appId ?: return@collect
                when (result) {
                    is InstallResult.Installed -> updates.update { it.installed(id) }
                    is InstallResult.Failed -> _messages.tryEmit(result.error.toUiText())
                    // The update is still offered; there is nothing to say.
                    is InstallResult.NeedsConfirmation -> Unit
                }
            }
        }
    }

    // ---- selection --------------------------------------------------------------------------

    fun onAppLongPress(id: Long) = toggleSelection(id)

    fun toggleSelection(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        selectedIds.value = lastKnownApps.mapTo(mutableSetOf()) { it.app.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /** Forgets the selected apps. Does not touch the device. */
    fun removeSelectedFromList() {
        val targets = selectedApps()
        clearSelection()
        viewModelScope.launch { deleteTrackedApps(targets) }
    }

    /** Uninstalls the selected apps that are actually installed. */
    fun uninstallSelected() {
        val targets = selectedApps().map { it.packageName }
        clearSelection()
        viewModelScope.launch { uninstallApps(targets) }
    }

    // ---- hints ------------------------------------------------------------------------------

    fun dismissLongPressHint() {
        viewModelScope.launch { settingsRepository.setLongPressHintSeen(true) }
    }

    private fun selectedApps(): List<TrackedApp> {
        val selected = selectedIds.value
        return lastKnownApps.filter { it.app.id in selected }.map { it.app }
    }

    /** Saved apps only: one opened from search has no row on this list until it is installed. */
    private fun Map<InstallKey, InstallProgress>.byAppId(): Map<Long, Float?> =
        entries.mapNotNull { (key, progress) ->
            (key as? InstallKey.App)?.let { it.appId to progress.fraction }
        }.toMap()

    private companion object {
        const val TAG = "AppsList"
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
