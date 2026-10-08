package dev.re7gog.b_sideloader.ui.feature.filtersuggestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.ai.LanguageModelGateway
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.selection.AbiMatcher
import dev.re7gog.b_sideloader.domain.suggestion.FilterProposal
import dev.re7gog.b_sideloader.domain.suggestion.RejectedSuggestion
import dev.re7gog.b_sideloader.domain.suggestion.SampleGroup
import dev.re7gog.b_sideloader.domain.suggestion.SourceSnapshot
import dev.re7gog.b_sideloader.domain.suggestion.samples
import dev.re7gog.b_sideloader.domain.usecase.LoadSuggestionSourceUseCase
import dev.re7gog.b_sideloader.domain.usecase.SuggestFiltersUseCase
import dev.re7gog.b_sideloader.domain.usecase.SuggestionRequest
import dev.re7gog.b_sideloader.domain.usecase.SuggestionStep
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The "suggest filters" sheet.
 *
 * Scoped to the details page that opens it, and reset by every [start], so each opening begins
 * fresh from the draft as it is then. Turned-down suggestions are remembered for the rest of that
 * opening, so a second "try again" still knows what the first was told.
 */
@HiltViewModel
class FilterSuggestionViewModel @Inject constructor(
    private val loadSource: LoadSuggestionSourceUseCase,
    private val suggestFilters: SuggestFiltersUseCase,
    private val languageModel: LanguageModelGateway,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilterSuggestionUiState())
    val uiState: StateFlow<FilterSuggestionUiState> = _uiState.asStateFlow()

    private var app: TrackedApp? = null
    private var snapshot: SourceSnapshot? = null
    private var exampleGroup: SampleGroup? = null
    private var current: FilterProposal? = null
    private val rejected = mutableListOf<RejectedSuggestion>()

    /** The source fetch or the suggestion run in flight; one at a time. */
    private var job: Job? = null

    /** Starts over for [app], the details page's draft as it is now. */
    fun start(app: TrackedApp) {
        job?.cancel()
        this.app = app
        snapshot = null
        exampleGroup = null
        current = FilterProposal.of(app)
        rejected.clear()
        _uiState.value = FilterSuggestionUiState(isTelegram = app.source is AppSource.Telegram)
        load()
    }

    /** Fetches the source again after a failure. */
    fun retryLoad() = load()

    private fun load() {
        val app = app ?: return
        job?.cancel()
        _uiState.update { it.copy(phase = SuggestionPhase.Loading, error = null) }
        job = viewModelScope.launch {
            val ai = languageModel.availability()
            try {
                val loaded = loadSource(app)
                snapshot = loaded
                val newest = loaded.samples(app).firstOrNull()
                exampleGroup = newest
                _uiState.update { state ->
                    state.copy(
                        phase = SuggestionPhase.Input,
                        ai = ai,
                        exampleGroupLabel = newest?.label,
                        exampleFiles = newest?.files.orEmpty().map { file ->
                            ExampleFileUi(file.name, AbiMatcher.runsOn(file.name, deviceInfo.supportedAbis))
                        }.toImmutableList(),
                    )
                }
            } catch (e: Throwable) {
                e.rethrowIfCancellation()
                logger.w(TAG, e) { "Could not load the source for a suggestion" }
                _uiState.update { it.copy(phase = SuggestionPhase.LoadFailed, ai = ai, error = e.toUiText()) }
            }
        }
    }

    fun onRequestChange(value: String) = _uiState.update { it.copy(request = value) }

    /** Picks the example at [index], or un-picks it when it already was. */
    fun onExampleSelect(index: Int) = _uiState.update {
        it.copy(selectedExample = if (it.selectedExample == index) null else index)
    }

    fun onFeedbackChange(value: String) = _uiState.update { it.copy(feedback = value) }

    fun onSuggest() {
        if (!_uiState.value.canSuggest) return
        run()
    }

    /** Turns the suggestion on screen down, with the feedback typed for it, and asks again. */
    fun onRetry() {
        val state = _uiState.value
        val suggestion = state.suggestion ?: return
        if (!state.canRetry) return
        rejected += RejectedSuggestion(suggestion.proposal, state.feedback)
        _uiState.update { it.copy(feedback = "") }
        run()
    }

    /** Stops a run, or leaves a result, and goes back to the request. */
    fun onBackToInput() {
        job?.cancel()
        _uiState.update {
            it.copy(phase = SuggestionPhase.Input, progress = null, suggestion = null, error = null, problems = persistentListOf())
        }
    }

    private fun run() {
        val app = app ?: return
        val snapshot = snapshot ?: return
        val current = current ?: return
        val state = _uiState.value
        val example = state.selectedExample?.let { exampleGroup?.files?.getOrNull(it) }
        val request = SuggestionRequest(
            app = app,
            snapshot = snapshot,
            example = example,
            instructions = state.request.takeIf { state.isAiAvailable }.orEmpty(),
            rejected = rejected.toList(),
            language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" },
        )
        job?.cancel()
        _uiState.update {
            it.copy(phase = SuggestionPhase.Working, progress = null, suggestion = null, error = null, problems = persistentListOf())
        }
        job = viewModelScope.launch {
            try {
                suggestFilters(request).collect { step -> onStep(step, current) }
            } catch (e: Throwable) {
                e.rethrowIfCancellation()
                logger.w(TAG, e) { "Filter suggestion failed" }
                _uiState.update { it.copy(phase = SuggestionPhase.Failed, progress = null, error = e.toUiText()) }
            }
        }
    }

    private fun onStep(step: SuggestionStep, current: FilterProposal) {
        when (step) {
            is SuggestionStep.DownloadingModel -> _uiState.update {
                it.copy(progress = UiText.of(R.string.suggest_progress_downloading, step.bytes / BYTES_PER_MB))
            }

            is SuggestionStep.Generating -> _uiState.update {
                val backend = (it.ai as? AiAvailability.Available)?.backend
                val text = if (backend is AiBackend.Cloud) R.string.suggest_progress_cloud else R.string.suggest_progress_on_device
                it.copy(progress = UiText.of(text, step.attempt, step.maxAttempts))
            }

            is SuggestionStep.Suggested -> _uiState.update {
                it.copy(
                    phase = SuggestionPhase.Result,
                    progress = null,
                    suggestion = step.suggestion.toUi(current, it.isTelegram),
                )
            }

            is SuggestionStep.NoWorkingFilter -> _uiState.update { state ->
                state.copy(
                    phase = SuggestionPhase.Failed,
                    progress = null,
                    error = UiText.of(R.string.suggest_no_working_filter),
                    problems = step.last?.problems.orEmpty()
                        .map { it.toUiText(state.isTelegram) }
                        .distinct()
                        .toImmutableList(),
                )
            }
        }
    }

    private companion object {
        const val TAG = "FilterSuggestion"
        const val BYTES_PER_MB = 1_048_576L
    }
}

