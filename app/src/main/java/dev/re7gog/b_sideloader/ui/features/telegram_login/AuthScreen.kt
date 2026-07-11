package dev.re7gog.b_sideloader.ui.features.telegram_login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onExit: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.step) {
        if (uiState.step is AuthStep.Ready) onAuthSuccess()
    }

    val canStepBack = uiState.step is AuthStep.CodeInput || uiState.step is AuthStep.PasswordInput
    val handleBack: () -> Unit = { if (canStepBack) viewModel.goBackToPhone() else onExit() }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_to_telegram)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.telegram),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            when (uiState.step) {
                is AuthStep.Loading -> {
                    Text(
                        text = stringResource(R.string.initializing_telegram),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                is AuthStep.PhoneInput -> AuthInputStep(
                    title = stringResource(R.string.auth_phone_title),
                    description = stringResource(R.string.auth_phone_description),
                    placeholder = stringResource(R.string.auth_phone_placeholder),
                    keyboardType = KeyboardType.Phone,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onConfirm = { viewModel.sendPhoneNumber(it) }
                )

                is AuthStep.CodeInput -> AuthInputStep(
                    title = stringResource(R.string.auth_code_title),
                    description = stringResource(R.string.auth_code_description),
                    placeholder = stringResource(R.string.auth_code_placeholder),
                    keyboardType = KeyboardType.Number,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onConfirm = { viewModel.sendCode(it) },
                    showBackToPhone = true,
                    onBackToPhone = { viewModel.goBackToPhone() }
                )

                is AuthStep.PasswordInput -> AuthInputStep(
                    title = stringResource(R.string.auth_password_title),
                    description = stringResource(R.string.auth_password_description),
                    placeholder = stringResource(R.string.auth_password_placeholder),
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onConfirm = { viewModel.sendPassword(it) },
                    showBackToPhone = true,
                    onBackToPhone = { viewModel.goBackToPhone() }
                )

                else -> {}
            }
        }
    }
}

@Composable
fun AuthInputStep(
    title: String,
    description: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showBackToPhone: Boolean = false,
    onBackToPhone: () -> Unit = {}
) {
    var value by remember { mutableStateOf("") }

    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isLoading,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { if (!isLoading && value.isNotBlank()) onConfirm(value) }
        )
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onConfirm(value) },
        enabled = !isLoading && value.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.auth_continue))
        }
    }
    if (showBackToPhone) {
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBackToPhone, enabled = !isLoading) {
            Text(stringResource(R.string.auth_change_phone))
        }
    }
}
