package dev.re7gog.b_sideloader.ui.feature.filtersuggestion

import androidx.compose.runtime.Immutable
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.suggestion.FilterProposal
import dev.re7gog.b_sideloader.domain.suggestion.PreviewRow
import dev.re7gog.b_sideloader.domain.suggestion.ProposalProblem
import dev.re7gog.b_sideloader.domain.usecase.FilterSuggestion
import dev.re7gog.b_sideloader.ui.common.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Where the sheet is: what it shows and which buttons it offers. */
enum class SuggestionPhase {
    /** Fetching the source. */
    Loading,

    /** The source could not be fetched; [FilterSuggestionUiState.error] says why. */
    LoadFailed,

    /** Choosing an example and typing a request. */
    Input,

    /** A run is in progress; [FilterSuggestionUiState.progress] says how far. */
    Working,

    /** A suggestion to accept or turn down. */
    Result,

    /** The run ended without a working filter, or with an error. */
    Failed,
}

@Immutable
data class FilterSuggestionUiState(
    val phase: SuggestionPhase = SuggestionPhase.Loading,
    val isTelegram: Boolean = false,
    /** Null until known. */
    val ai: AiAvailability? = null,
    /** The newest release's name or message's first line, which the examples come from. */
    val exampleGroupLabel: String? = null,
    val exampleFiles: ImmutableList<ExampleFileUi> = persistentListOf(),
    /** Index into [exampleFiles]; null when no example is picked. */
    val selectedExample: Int? = null,
    val request: String = "",
    /** What is wrong with the suggestion on screen, for the next try. */
    val feedback: String = "",
    val progress: UiText? = null,
    val suggestion: SuggestionUi? = null,
    val error: UiText? = null,
    /** What the last failed proposal got wrong, one line each. */
    val problems: ImmutableList<UiText> = persistentListOf(),
) {
    val isAiAvailable: Boolean get() = ai?.isAvailable == true

    /** An example is enough on its own; words need a model. */
    val canSuggest: Boolean
        get() = phase == SuggestionPhase.Input &&
            (selectedExample != null || (request.isNotBlank() && isAiAvailable))

    /** Another try means asking a model, with what was wrong this time. */
    val canRetry: Boolean get() = isAiAvailable
}

@Immutable
data class ExampleFileUi(
    val name: String,
    /** False when built only for architectures this phone lacks. */
    val runsOnDevice: Boolean,
)

@Immutable
data class SuggestionUi(
    val proposal: FilterProposal,
    /** Every field the suggestion changes, in the order the details page lists them. */
    val changes: ImmutableList<FilterChangeUi>,
    val explanation: String?,
    /** Null when it came from the example alone. */
    val backend: AiBackend?,
    val preview: ImmutableList<PreviewRow>,
)

/** One field of the filters, before and after. Null means empty. */
@Immutable
data class FilterChangeUi(
    val label: UiText,
    val before: UiText?,
    val after: UiText?,
)

/** Domain -> screen: only what changes, labelled the way the details page labels its fields. */
fun FilterSuggestion.toUi(current: FilterProposal, isTelegram: Boolean): SuggestionUi {
    val after = proposal
    val advanced = after.mode == FilterMode.Regex
    val changes = buildList {
        if (current.mode != after.mode) {
            add(FilterChangeUi(UiText.of(R.string.advanced_filters), current.mode.label(), after.mode.label()))
        }
        if (!isTelegram && current.usePrereleases != after.usePrereleases) {
            add(FilterChangeUi(UiText.of(R.string.prereleases), current.usePrereleases.label(), after.usePrereleases.label()))
        }
        // A field whose text stays but whose mode changes means something new, so it is listed too.
        fun field(label: Int, before: String, now: String) {
            if (before != now || (current.mode != after.mode && now.isNotBlank())) {
                add(FilterChangeUi(UiText.of(label), before.asText(), now.asText()))
            }
        }
        field(
            if (isTelegram) {
                if (advanced) R.string.message_regex_include else R.string.message_must_contain
            } else {
                if (advanced) R.string.release_regex_include else R.string.release_must_contain
            },
            current.sourceFilter.include,
            after.sourceFilter.include,
        )
        field(
            if (isTelegram) {
                if (advanced) R.string.message_regex_exclude else R.string.message_must_not_contain
            } else {
                if (advanced) R.string.release_regex_exclude else R.string.release_must_not_contain
            },
            current.sourceFilter.exclude,
            after.sourceFilter.exclude,
        )
        field(
            if (advanced) R.string.apk_regex_include else R.string.apk_must_contain,
            current.assetFilter.include,
            after.assetFilter.include,
        )
        field(
            if (advanced) R.string.apk_regex_exclude else R.string.apk_must_not_contain,
            current.assetFilter.exclude,
            after.assetFilter.exclude,
        )
    }
    return SuggestionUi(
        proposal = after,
        changes = changes.toImmutableList(),
        explanation = explanation,
        backend = backend,
        preview = verification.preview.toImmutableList(),
    )
}

private fun FilterMode.label(): UiText =
    UiText.of(if (this == FilterMode.Regex) R.string.suggest_mode_regex else R.string.suggest_mode_words)

private fun Boolean.label(): UiText = UiText.of(if (this) R.string.suggest_state_on else R.string.suggest_state_off)

private fun String.asText(): UiText? = takeIf { it.isNotBlank() }?.let(UiText::Raw)

/** A problem as the user reads it: what went wrong, not the instruction the model got. */
fun ProposalProblem.toUiText(isTelegram: Boolean): UiText = when (this) {
    is ProposalProblem.InvalidRegex -> UiText.of(R.string.suggest_problem_invalid_regex, pattern)
    ProposalProblem.NothingMatches -> UiText.of(
        if (isTelegram) R.string.suggest_problem_nothing_matches_message else R.string.suggest_problem_nothing_matches,
    )

    is ProposalProblem.ExampleGroupRejected -> UiText.of(
        if (isTelegram) R.string.suggest_problem_example_message_rejected else R.string.suggest_problem_example_release_rejected,
    )

    is ProposalProblem.ExampleFileRejected -> UiText.of(R.string.suggest_problem_example_file_rejected, example)
    is ProposalProblem.OtherFileChosen -> UiText.of(R.string.suggest_problem_other_file_chosen, chosen)
    is ProposalProblem.Unreadable -> UiText.of(R.string.suggest_problem_unreadable)
}
