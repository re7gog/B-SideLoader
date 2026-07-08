package dev.re7gog.b_sideloader.ui.features.telegram_login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val telegramManager: TelegramManager
) : ViewModel() {
    // The step TDLib currently requires
    private val _tdStep = MutableStateFlow<AuthStep>(AuthStep.Loading)
    // A UI-only override when the user steps backward (e.g. back to phone number)
    private val _manualStep = MutableStateFlow<AuthStep?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        _tdStep, _manualStep, _isLoading, _errorMessage
    ) { tdStep, manualStep, isLoading, error ->
        AuthUiState(step = manualStep ?: tdStep, isLoading = isLoading, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState())

    init {
        observeAuthState()
        observeErrors()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            telegramManager.authState.collect { state ->
                // A real state change from TDLib supersedes any manual back navigation
                _manualStep.value = null
                _isLoading.value = false
                _errorMessage.value = null
                _tdStep.value = when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber -> AuthStep.PhoneInput
                    is TdApi.AuthorizationStateWaitCode -> AuthStep.CodeInput
                    is TdApi.AuthorizationStateWaitPassword -> AuthStep.PasswordInput
                    is TdApi.AuthorizationStateReady -> AuthStep.Ready
                    is TdApi.AuthorizationStateLoggingOut,
                    is TdApi.AuthorizationStateClosed -> AuthStep.PhoneInput
                    else -> AuthStep.Loading
                }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            telegramManager.authErrors.collect { message ->
                _isLoading.value = false
                _errorMessage.value = message
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        _errorMessage.value = null
        _isLoading.value = true
        telegramManager.setPhoneNumber(normalizePhone(phone))
    }

    /**
     * Accepts a phone number in any format the user typed (spaces, dashes,
     * parentheses, leading 00 or +) and normalizes it to the strict
     * international "+<digits>" form TDLib expects.
     */
    private fun normalizePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        // "00" is the international access prefix some users type instead of "+"
        if (!raw.trimStart().startsWith("+") && digits.startsWith("00")) {
            digits = digits.drop(2)
        }
        return "+$digits"
    }

    fun sendCode(code: String) {
        _errorMessage.value = null
        _isLoading.value = true
        telegramManager.checkCode(code)
    }

    fun sendPassword(password: String) {
        _errorMessage.value = null
        _isLoading.value = true
        telegramManager.checkPassword(password)
    }

    /** Whether the current step can be stepped back to phone-number entry. */
    val canStepBack: Boolean
        get() = uiState.value.step.let {
            it is AuthStep.CodeInput || it is AuthStep.PasswordInput
        }

    /** Step backward within the flow to re-enter the phone number. */
    fun goBackToPhone() {
        _errorMessage.value = null
        _isLoading.value = false
        _manualStep.value = AuthStep.PhoneInput
    }
}
