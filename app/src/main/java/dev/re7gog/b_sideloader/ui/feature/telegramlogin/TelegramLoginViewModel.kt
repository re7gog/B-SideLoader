package dev.re7gog.b_sideloader.ui.feature.telegramlogin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramCodeDelivery
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which step of the sign-in flow is on screen. */
@Immutable
sealed interface AuthStep {
    data object Loading : AuthStep
    data object PhoneNumber : AuthStep
    data class Code(
        val delivery: TelegramCodeDelivery,
        val canResend: Boolean,
        val resendSeconds: Int,
    ) : AuthStep
    data object Password : AuthStep
    data object Ready : AuthStep
}

@Immutable
data class TelegramLoginUiState(
    val step: AuthStep = AuthStep.Loading,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Code and password can both step back to re-entering the phone number. */
    val canGoBackToPhone: Boolean get() = step is AuthStep.Code || step is AuthStep.Password
}

@HiltViewModel
class TelegramLoginViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
) : ViewModel() {

    /** The step TDLib is asking for. */
    private val serverStep = MutableStateFlow<AuthStep>(AuthStep.Loading)

    /** A UI-only override for when the user steps backwards. Cleared by any real state change. */
    private val manualStep = MutableStateFlow<AuthStep?>(null)

    private val isSubmitting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TelegramLoginUiState> = combine(
        serverStep, manualStep, isSubmitting, errorMessage,
    ) { server, manual, submitting, error ->
        TelegramLoginUiState(
            step = manual ?: server,
            isSubmitting = submitting,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TelegramLoginUiState())

    init {
        viewModelScope.launch {
            telegramRepository.authState.collectLatest { state ->
                // A real state change from TDLib supersedes any manual back navigation.
                manualStep.value = null
                isSubmitting.value = false
                errorMessage.value = null
                serverStep.value = state.toStep()
                if (state is TelegramAuthState.WaitingForCode && state.canResend) {
                    for (remaining in state.resendTimeoutSeconds downTo 1) {
                        delay(SECOND_MS)
                        serverStep.value = AuthStep.Code(state.delivery, true, remaining - 1)
                    }
                }
            }
        }
        viewModelScope.launch {
            telegramRepository.authErrors.collect { message ->
                isSubmitting.value = false
                errorMessage.value = message
            }
        }
    }

    fun submitPhoneNumber(raw: String) = submit { telegramRepository.sendPhoneNumber(normalizePhone(raw)) }

    fun submitCode(code: String) = submit { telegramRepository.sendCode(code.trim()) }

    fun submitPassword(password: String) = submit { telegramRepository.sendPassword(password) }

    fun resendCode() {
        val step = serverStep.value as? AuthStep.Code ?: return
        if (manualStep.value != null || isSubmitting.value || !step.canResend || step.resendSeconds > 0) return
        errorMessage.value = null
        isSubmitting.value = true
        viewModelScope.launch {
            try {
                telegramRepository.resendCode()
            } catch (error: AppError.Telegram) {
                errorMessage.value = error.reason
            } finally {
                isSubmitting.value = false
            }
        }
    }

    fun goBackToPhone() {
        errorMessage.value = null
        isSubmitting.value = false
        manualStep.value = AuthStep.PhoneNumber
    }

    private inline fun submit(crossinline action: suspend () -> Unit) {
        errorMessage.value = null
        isSubmitting.value = true
        viewModelScope.launch { action() }
    }

    /**
     * Accepts a phone number in whatever shape the user typed it — spaces, dashes, parentheses, a
     * leading `00` or `+` — and normalises it to the strict `+<digits>` form TDLib requires.
     */
    private fun normalizePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        // "00" is the international access prefix some users type instead of "+".
        if (!raw.trimStart().startsWith("+") && digits.startsWith(INTERNATIONAL_PREFIX)) {
            digits = digits.drop(INTERNATIONAL_PREFIX.length)
        }
        return "+$digits"
    }

    private fun TelegramAuthState.toStep(): AuthStep = when (this) {
        TelegramAuthState.Initialising -> AuthStep.Loading
        TelegramAuthState.WaitingForPhoneNumber -> AuthStep.PhoneNumber
        is TelegramAuthState.WaitingForCode -> AuthStep.Code(delivery, canResend, resendTimeoutSeconds)
        TelegramAuthState.WaitingForPassword -> AuthStep.Password
        TelegramAuthState.Ready -> AuthStep.Ready
        TelegramAuthState.LoggedOut -> AuthStep.PhoneNumber
    }

    private companion object {
        const val INTERNATIONAL_PREFIX = "00"
        const val SECOND_MS = 1_000L
    }
}
