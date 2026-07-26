package dev.re7gog.b_sideloader.ui.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.TelegramAccount
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.domain.usecase.SyncBackgroundWorkUseCase
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val telegramAccount: TelegramAccount? = null,
    val githubToken: String = "",
) {
    val isTelegramSignedIn: Boolean get() = telegramAccount != null
}

/**
 * Settings.
 *
 * Notable change: switching to a privileged installer *verifies* it before storing the choice, and
 * reports why it failed as a typed [dev.re7gog.b_sideloader.domain.error.AppError] rather than as
 * a hand-written toast string per branch. And every switch that affects scheduling re-syncs the
 * background work immediately, so a toggle takes effect now rather than at the next cold start.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val telegramRepository: TelegramRepository,
    private val installerGateway: InstallerGateway,
    private val syncBackgroundWork: SyncBackgroundWorkUseCase,
) : ViewModel() {

    private val githubToken = MutableStateFlow("")

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    private val telegramAccount: StateFlow<TelegramAccount?> = telegramRepository.authState
        .map { it is TelegramAuthState.Ready }
        .distinctUntilChanged()
        .map { signedIn -> if (signedIn) telegramRepository.getAccount() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        telegramAccount,
        githubToken,
    ) { settings, account, token ->
        SettingsUiState(settings = settings, telegramAccount = account, githubToken = token)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_MS), SettingsUiState())

    init {
        viewModelScope.launch {
            githubToken.value = secretsRepository.getGithubToken().orEmpty()
        }
    }

    /**
     * Applies the chosen installer mode.
     *
     * Session needs no privileges and is stored directly; a privileged mode is verified first and
     * only stored when its service is present and permission granted, so the user cannot end up in
     * a state where every install silently fails.
     */
    fun selectInstallerMode(mode: InstallerMode) {
        viewModelScope.launch {
            if (mode == uiState.value.settings.installerMode) return@launch
            if (!mode.isPrivileged) {
                settingsRepository.setInstallerMode(mode)
                return@launch
            }
            when (val access = installerGateway.checkPrivilegedAccess(mode)) {
                is PrivilegedAccess.Granted -> {
                    settingsRepository.setInstallerMode(mode)
                    _messages.tryEmit(UiText.of(R.string.installer_enabled, mode.name))
                }

                is PrivilegedAccess.Unavailable -> _messages.tryEmit(access.error.toUiText())
            }
        }
    }

    fun setAutoUpdate(enabled: Boolean) = updateAndResync {
        settingsRepository.setAutoUpdate(enabled)
    }

    fun setAllowMeteredNetwork(enabled: Boolean) = updateAndResync {
        settingsRepository.setAllowMeteredNetwork(enabled)
    }

    fun setBackgroundMode(mode: BackgroundMode) = updateAndResync {
        settingsRepository.setBackgroundMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setParallelUpdateChecks(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setParallelUpdateChecks(enabled) }
    }

    fun updateGithubToken(token: String) {
        githubToken.value = token
        viewModelScope.launch {
            suspendRunCatching { secretsRepository.setGithubToken(token) }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    fun signOutOfTelegram() {
        viewModelScope.launch {
            suspendRunCatching { telegramRepository.logOut() }
                .onSuccess { _messages.tryEmit(UiText.of(R.string.logging_out_telegram)) }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    private inline fun updateAndResync(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            suspendRunCatching { syncBackgroundWork() }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    private companion object {
        const val SUBSCRIPTION_MS = 5_000L
    }
}
