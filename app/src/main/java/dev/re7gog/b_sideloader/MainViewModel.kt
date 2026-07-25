package dev.re7gog.b_sideloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.background.BackgroundWorkScheduler
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Activity-scoped state: the theme, and the "check now" entry point from the notification. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val backgroundWorkScheduler: BackgroundWorkScheduler,
    private val logger: Logger,
) : ViewModel() {

    val useDynamicColor: StateFlow<Boolean> = settingsRepository.settings
        .map { it.useDynamicColor }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_MS),
            initialValue = false,
        )

    /**
     * Runs a check immediately, outside the normal schedule. Goes through WorkManager rather than
     * running inline so it survives the user leaving the app right after tapping the notification.
     */
    fun runUpdateCheckNow() {
        viewModelScope.launch {
            suspendRunCatching { backgroundWorkScheduler.runOnce() }
                .onFailure { logger.w(TAG) { "Could not enqueue an immediate update check" } }
        }
    }

    private companion object {
        const val TAG = "Main"
        const val SUBSCRIPTION_MS = 5_000L
    }
}
