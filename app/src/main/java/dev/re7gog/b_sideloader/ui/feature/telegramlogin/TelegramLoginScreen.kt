package dev.re7gog.b_sideloader.ui.feature.telegramlogin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
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
fun TelegramLoginScreen(
    onSignedIn: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TelegramLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.step) {
        if (uiState.step is AuthStep.Ready) onSignedIn()
    }

    val handleBack: () -> Unit = {
        if (uiState.canGoBackToPhone) viewModel.goBackToPhone() else onExit()
    }
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_to_telegram)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.telegram),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            when (uiState.step) {
                AuthStep.Loading -> {
                    Text(
                        text = stringResource(R.string.initializing_telegram),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                AuthStep.PhoneNumber -> AuthInputStep(
                    title = stringResource(R.string.auth_phone_title),
                    description = stringResource(R.string.auth_phone_description),
                    placeholder = stringResource(R.string.auth_phone_placeholder),
                    keyboardType = KeyboardType.Phone,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onConfirm = viewModel::submitPhoneNumber,
                )

                AuthStep.Code -> AuthInputStep(
                    title = stringResource(R.string.auth_code_title),
                    description = stringResource(R.string.auth_code_description),
                    placeholder = stringResource(R.string.auth_code_placeholder),
                    keyboardType = KeyboardType.Number,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onConfirm = viewModel::submitCode,
                    onBackToPhone = viewModel::goBackToPhone,
                )

                AuthStep.Password -> AuthInputStep(
                    title = stringResource(R.string.auth_password_title),
                    description = stringResource(R.string.auth_password_description),
                    placeholder = stringResource(R.string.auth_password_placeholder),
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onConfirm = viewModel::submitPassword,
                    onBackToPhone = viewModel::goBackToPhone,
                )

                AuthStep.Ready -> CircularProgressIndicator()
            }
        }
    }
}

/**
 * One step of the flow.
 *
 * The entered value is `rememberSaveable`, so rotating the device mid-code no longer wipes what
 * the user typed and forces them to request a new code.
 */
@Composable
private fun ColumnScope.AuthInputStep(
    title: String,
    description: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onBackToPhone: (() -> Unit)? = null,
) {
    var value by rememberSaveable(title) { mutableStateOf("") }
    val canSubmit = !isSubmitting && value.isNotBlank()

    Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isSubmitting,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onConfirm(value) }),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onConfirm(value) },
        enabled = canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.auth_continue))
        }
    }
    if (onBackToPhone != null) {
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBackToPhone, enabled = !isSubmitting) {
            Text(stringResource(R.string.auth_change_phone))
        }
    }
}
