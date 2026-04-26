package dev.re7gog.b_sideloader.ui.features.telegram_login

sealed class AuthStep {
    object Loading : AuthStep()
    object PhoneInput : AuthStep()
    object CodeInput : AuthStep()
    object PasswordInput : AuthStep()  // 2FA
    object Ready : AuthStep()
    data class Error(val message: String) : AuthStep()
}

data class AuthUiState(
    val step: AuthStep = AuthStep.Loading,
    val isLoading: Boolean = false
)