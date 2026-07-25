package dev.re7gog.b_sideloader.ui.common.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.re7gog.b_sideloader.ui.common.text.UiText
import dev.re7gog.b_sideloader.ui.common.text.asString
import kotlinx.coroutines.flow.Flow

/**
 * Bridges a ViewModel's `Flow<UiText>` of one-off messages to a snackbar.
 *
 * The indirection exists because a [UiText] can only be resolved inside composition (it may be a
 * string resource with arguments) while `showSnackbar` must be called from a coroutine. Holding
 * the pending message in state lets the resource be resolved by the composable and the snackbar be
 * shown by an effect, and it means a message survives a configuration change instead of being lost
 * with the collector.
 */
@Composable
fun SnackbarMessages(
    messages: Flow<UiText>,
    hostState: SnackbarHostState,
    duration: SnackbarDuration = SnackbarDuration.Short,
) {
    var pending by remember { mutableStateOf<UiText?>(null) }

    LaunchedEffect(messages) {
        messages.collect { pending = it }
    }

    val current = pending
    if (current != null) {
        val text = current.asString()
        LaunchedEffect(current) {
            hostState.showSnackbar(message = text, duration = duration)
            pending = null
        }
    }
}
