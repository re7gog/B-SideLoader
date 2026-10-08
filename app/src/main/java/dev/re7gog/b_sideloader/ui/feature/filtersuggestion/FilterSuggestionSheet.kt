package dev.re7gog.b_sideloader.ui.feature.filtersuggestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.suggestion.FilterProposal
import dev.re7gog.b_sideloader.domain.suggestion.PreviewRow
import dev.re7gog.b_sideloader.ui.common.component.SectionDefaults
import dev.re7gog.b_sideloader.ui.common.component.SectionLabel
import dev.re7gog.b_sideloader.ui.common.text.asString
import dev.re7gog.b_sideloader.ui.feature.aisettings.labelRes

/**
 * "Suggest filters", over the details page.
 *
 * Nothing it does touches the app until the user taps Apply, and even then only the page's draft:
 * the page's own Save (or install) is what keeps it. So a suggestion is always something to look
 * at first — the filters it changes and what each recent release would install with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSuggestionSheet(
    app: TrackedApp,
    onApply: (FilterProposal) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FilterSuggestionViewModel = hiltViewModel(key = VIEW_MODEL_KEY),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Once per opening: saved across a rotation, which must not start over, but not across a
    // close — the ViewModel outlives the sheet, and the next opening starts from the draft then.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            viewModel.start(app)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FilterSuggestionContent(
            uiState = uiState,
            onExampleSelect = viewModel::onExampleSelect,
            onRequestChange = viewModel::onRequestChange,
            onSuggest = viewModel::onSuggest,
            onFeedbackChange = viewModel::onFeedbackChange,
            onRetry = viewModel::onRetry,
            onBackToInput = viewModel::onBackToInput,
            onRetryLoad = viewModel::retryLoad,
            onApply = { uiState.suggestion?.proposal?.let(onApply) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun FilterSuggestionContent(
    uiState: FilterSuggestionUiState,
    onExampleSelect: (Int) -> Unit,
    onRequestChange: (String) -> Unit,
    onSuggest: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBackToInput: () -> Unit,
    onRetryLoad: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item {
            Text(
                text = stringResource(R.string.suggest_filters_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        when (uiState.phase) {
            SuggestionPhase.Loading -> item { Progress(stringResource(R.string.suggest_loading)) }

            SuggestionPhase.LoadFailed -> item {
                Message(
                    text = uiState.error?.asString().orEmpty(),
                    primaryLabel = stringResource(R.string.suggest_retry_load),
                    onPrimary = onRetryLoad,
                    secondaryLabel = stringResource(R.string.cancel),
                    onSecondary = onDismiss,
                )
            }

            SuggestionPhase.Input -> inputItems(uiState, onExampleSelect, onRequestChange, onSuggest, onDismiss)

            SuggestionPhase.Working -> item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Progress(uiState.progress?.asString() ?: stringResource(R.string.suggest_working))
                    OutlinedButton(onClick = onBackToInput) { Text(stringResource(R.string.cancel)) }
                }
            }

            SuggestionPhase.Result -> uiState.suggestion?.let { suggestion ->
                resultItems(uiState, suggestion, onFeedbackChange, onRetry, onBackToInput, onApply)
            }

            SuggestionPhase.Failed -> {
                item {
                    Text(
                        text = uiState.error?.asString().orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                items(uiState.problems) { problem ->
                    Text(
                        text = "• " + problem.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackToInput) { Text(stringResource(R.string.suggest_edit_request)) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.inputItems(
    uiState: FilterSuggestionUiState,
    onExampleSelect: (Int) -> Unit,
    onRequestChange: (String) -> Unit,
    onSuggest: () -> Unit,
    onDismiss: () -> Unit,
) {
    item {
        Text(
            text = stringResource(
                if (uiState.isAiAvailable) R.string.suggest_intro else R.string.suggest_intro_example_only,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        SectionLabel(
            if (uiState.isTelegram) {
                stringResource(R.string.suggest_example_message)
            } else {
                stringResource(
                    R.string.suggest_example_release,
                    uiState.exampleGroupLabel?.ifBlank { null } ?: stringResource(R.string.suggest_untitled),
                )
            },
        )
    }
    if (uiState.exampleFiles.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.no_matching_apks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(uiState.exampleFiles) { index, file ->
        ExampleRow(
            file = file,
            selected = uiState.selectedExample == index,
            onSelect = { onExampleSelect(index) },
        )
    }
    item {
        OutlinedTextField(
            value = uiState.request,
            onValueChange = onRequestChange,
            label = { Text(stringResource(R.string.suggest_request_label)) },
            placeholder = { Text(stringResource(R.string.suggest_request_placeholder)) },
            supportingText = { Text(aiNote(uiState.ai)) },
            enabled = uiState.isAiAvailable,
            shape = RoundedCornerShape(SectionDefaults.FieldCorner),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
    item {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onSuggest, enabled = uiState.canSuggest) {
                Icon(
                    painter = painterResource(R.drawable.auto_awesome_24px),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.suggest_action))
            }
        }
    }
}

private fun LazyListScope.resultItems(
    uiState: FilterSuggestionUiState,
    suggestion: SuggestionUi,
    onFeedbackChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBackToInput: () -> Unit,
    onApply: () -> Unit,
) {
    item {
        Text(
            text = origin(suggestion.backend),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    suggestion.explanation?.let { explanation ->
        item { Text(explanation, style = MaterialTheme.typography.bodyLarge) }
    }

    item { SectionLabel(stringResource(R.string.suggest_changes)) }
    if (suggestion.changes.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.suggest_no_changes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(suggestion.changes) { change -> ChangeRow(change) }

    item { SectionLabel(stringResource(if (uiState.isTelegram) R.string.suggest_preview_messages else R.string.suggest_preview_releases)) }
    items(suggestion.preview) { row -> PreviewRowItem(row, uiState.isTelegram) }

    if (uiState.canRetry) {
        item {
            OutlinedTextField(
                value = uiState.feedback,
                onValueChange = onFeedbackChange,
                label = { Text(stringResource(R.string.suggest_feedback_label)) },
                shape = RoundedCornerShape(SectionDefaults.FieldCorner),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
    item {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onBackToInput) { Text(stringResource(R.string.suggest_back)) }
            Spacer(Modifier.weight(1f))
            if (uiState.canRetry) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.suggest_try_again)) }
            }
            Button(onClick = onApply) {
                Text(stringResource(R.string.suggest_apply))
            }
        }
    }
}

@Composable
private fun ExampleRow(file: ExampleFileUi, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SectionDefaults.FieldCorner),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                if (!file.runsOnDevice) {
                    Text(
                        text = stringResource(R.string.suggest_example_other_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: FilterChangeUi) {
    Column {
        Text(change.label.asString(), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = change.before?.asString() ?: stringResource(R.string.suggest_empty_field),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough.takeIf { change.before != null },
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("  →  ", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = change.after?.asString() ?: stringResource(R.string.suggest_empty_field),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun PreviewRowItem(row: PreviewRow, isTelegram: Boolean) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.alpha(if (row.after == null) SKIPPED_ALPHA else 1f)) {
        Icon(
            painter = painterResource(if (row.isTarget) R.drawable.check_24px else R.drawable.apk_file_24px),
            contentDescription = if (row.isTarget) stringResource(R.string.cd_will_install) else null,
            tint = if (row.isTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = buildString {
                    append(
                        row.label?.ifBlank { null }
                            ?: stringResource(if (isTelegram) R.string.suggest_message_untitled else R.string.suggest_untitled),
                    )
                    if (row.isPrerelease) append(" · ").append(stringResource(R.string.suggest_prerelease))
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.after ?: stringResource(R.string.suggest_skipped),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (row.before != row.after) {
                Text(
                    text = stringResource(R.string.suggest_was, row.before ?: stringResource(R.string.suggest_skipped)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Message(
    text: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

/** Says whether words can be used, and where they would go. */
@Composable
private fun aiNote(ai: AiAvailability?): String = when (ai) {
    null -> ""
    is AiAvailability.Available -> when (val backend = ai.backend) {
        AiBackend.OnDevice -> stringResource(
            if (ai.needsDownload) R.string.suggest_ai_on_device_download else R.string.suggest_ai_on_device,
        )

        is AiBackend.Cloud -> stringResource(R.string.suggest_ai_cloud, stringResource(backend.provider.labelRes))
    }

    is AiAvailability.Unavailable -> stringResource(
        when (ai.reason) {
            AiUnavailableReason.Disabled -> R.string.suggest_ai_off
            AiUnavailableReason.Unsupported -> R.string.suggest_ai_unsupported
            AiUnavailableReason.MissingApiKey -> R.string.suggest_ai_missing_key
        },
    )
}

@Composable
private fun origin(backend: AiBackend?): String = when (backend) {
    null -> stringResource(R.string.suggest_origin_example)
    AiBackend.OnDevice -> stringResource(R.string.suggest_origin_on_device)
    is AiBackend.Cloud -> stringResource(R.string.suggest_origin_cloud, stringResource(backend.provider.labelRes))
}

private const val VIEW_MODEL_KEY = "filter_suggestion"
private const val SKIPPED_ALPHA = 0.6f
