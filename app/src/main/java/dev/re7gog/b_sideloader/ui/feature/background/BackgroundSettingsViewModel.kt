package dev.re7gog.b_sideloader.ui.feature.background

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.domain.background.BackgroundHealth
import dev.re7gog.b_sideloader.domain.background.BackgroundRestrictions
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.usecase.ObserveBackgroundHealthUseCase
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BackgroundSettingsUiState(
    val health: BackgroundHealth? = null,
    val isRefreshing: Boolean = true,
)

/**
 * Backs the background-reliability checklist.
 *
 * Everything it reports is re-read on demand rather than observed: none of the underlying system
 * states (battery exemption, standby bucket, OEM autostart) is observable, and the moment they
 * change is exactly the moment the user comes back from the settings activity — so the screen
 * refreshes on resume.
 */
@HiltViewModel
class BackgroundSettingsViewModel @Inject constructor(
    private val observeBackgroundHealth: ObserveBackgroundHealthUseCase,
    private val restrictions: BackgroundRestrictions,
    private val settingsRepository: SettingsRepository,
    private val syncBackgroundWork: SyncBackgroundWorkUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundSettingsUiState())
    val uiState: StateFlow<BackgroundSettingsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            suspendRunCatching { observeBackgroundHealth() }
                .onSuccess { health -> _uiState.value = BackgroundSettingsUiState(health, false) }
                .onFailure {
                    _uiState.update { state -> state.copy(isRefreshing = false) }
                    _messages.tryEmit(it.toUiText())
                }
        }
    }

    fun requestBatteryExemption() {
        // The result arrives as a system dialog; the screen re-reads on resume either way.
        if (!restrictions.requestIgnoreBatteryOptimizations()) {
            _messages.tryEmit(UiText.of(R.string.background_could_not_open))
        }
    }

    fun openAutoStartSettings() {
        if (!restrictions.openAutoStartSettings()) {
            // No vendor screen resolved; the generic app page at least gets the user close.
            if (!restrictions.openAppSettings()) {
                _messages.tryEmit(UiText.of(R.string.background_could_not_open))
            }
        }
    }

    fun openNotificationSettings() {
        if (!restrictions.openNotificationSettings()) {
            _messages.tryEmit(UiText.of(R.string.background_could_not_open))
        }
    }

    fun openAppSettings() {
        if (!restrictions.openAppSettings()) {
            _messages.tryEmit(UiText.of(R.string.background_could_not_open))
        }
    }

    /** Switching to the persistent service is the one fix that works on every ROM. */
    fun usePersistentService() {
        viewModelScope.launch {
            settingsRepository.setBackgroundMode(BackgroundMode.Persistent)
            suspendRunCatching { syncBackgroundWork() }
                .onFailure { _messages.tryEmit(it.toUiText()) }
            refresh()
        }
    }
}
