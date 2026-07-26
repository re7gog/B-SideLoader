package dev.re7gog.b_sideloader.ui.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.usecase.AppInstallEvent
import dev.re7gog.b_sideloader.domain.usecase.CheckUpdatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.ObserveTrackedAppsUseCase
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
 */
@HiltViewModel
class AppsListViewModel @Inject constructor(
    observeTrackedApps: ObserveTrackedAppsUseCase,
    private val appsRepository: AppsRepository,
    private val checkUpdates: CheckUpdatesUseCase,
    private val installApp: InstallAppUseCase,
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
    private var installJob: Job? = null

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
    ) { apps, selected, board, hintSeen ->
        lastKnownApps = apps
        // Drop ids of apps that disappeared, otherwise the selection count outlives the rows.
        val liveSelection = selected intersect apps.mapTo(mutableSetOf()) { it.app.id }
        val items = apps.toListItems(liveSelection, board.states, board.progress)
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
    }

    // ---- update checking --------------------------------------------------------------------

    /**
     * Re-checks every installed app. A second call while one is running is ignored.
     *
     * The app list comes from a repository snapshot rather than from [lastKnownApps]: this is
     * called from `init`, before anything has subscribed to [uiState], so the observed list has
     * not arrived yet and the check would silently have nothing to do.
     */
    fun refresh() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
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
    fun updateApp(id: Long) = install(listOfNotNull(id))

    /** Installs every app the last check found an update for, one at a time. */
    fun updateAll() = install(uiState.value.apps.filter { it.canUpdate }.map { it.id })

    /**
     * Installs run strictly one after another: `PackageInstaller` sessions do not overlap sanely,
     * and on the unprivileged path each one raises its own confirmation dialog.
     */
    private fun install(ids: List<Long>) {
        if (ids.isEmpty() || installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            val apps = appsRepository.getApps().associateBy { it.id }
            ids.forEach { id ->
                val app = apps[id] ?: return@forEach
                val candidate = updates.value.candidates[id] ?: return@forEach
                installOne(app, candidate)
            }
        }
    }

    private suspend fun installOne(app: TrackedApp, candidate: UpdateCandidate) {
        updates.update { it.starting(app.id) }
        try {
            installApp(app, candidate).collect { event ->
                when (event) {
                    is AppInstallEvent.Progress ->
                        updates.update { it.progressing(app.id, event.progress.fraction) }

                    is AppInstallEvent.Completed -> updates.update { it.installed(app.id) }

                    is AppInstallEvent.Failed -> {
                        updates.update { it.failed(app.id) }
                        _messages.tryEmit(event.error.toUiText())
                    }
                }
            }
        } catch (e: Throwable) {
            // Cancellation lands here too; restoring the row's state is correct either way, and
            // rethrowing keeps the coroutine's cancellation propagating.
            updates.update { it.failed(app.id) }
            if (e !is kotlinx.coroutines.CancellationException) {
                logger.w(TAG, e) { "Update failed for ${app.name}" }
                _messages.tryEmit(e.toUiText())
            }
            throw e
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

    private companion object {
        const val TAG = "AppsList"
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
