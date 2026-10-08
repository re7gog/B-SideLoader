package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.ai.LanguageModelGateway
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AiAvailability
import dev.re7gog.b_sideloader.domain.model.AiBackend
import dev.re7gog.b_sideloader.domain.model.AiUnavailableReason
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.domain.suggestion.ExampleFilterDeriver
import dev.re7gog.b_sideloader.domain.suggestion.FailedAttempt
import dev.re7gog.b_sideloader.domain.suggestion.FilterPrompt
import dev.re7gog.b_sideloader.domain.suggestion.FilterProposal
import dev.re7gog.b_sideloader.domain.suggestion.PromptBudget
import dev.re7gog.b_sideloader.domain.suggestion.PromptContext
import dev.re7gog.b_sideloader.domain.suggestion.ProposalProblem
import dev.re7gog.b_sideloader.domain.suggestion.ProposalVerifier
import dev.re7gog.b_sideloader.domain.suggestion.RejectedSuggestion
import dev.re7gog.b_sideloader.domain.suggestion.SampleFile
import dev.re7gog.b_sideloader.domain.suggestion.SourceSnapshot
import dev.re7gog.b_sideloader.domain.suggestion.Verification
import dev.re7gog.b_sideloader.domain.suggestion.kind
import dev.re7gog.b_sideloader.domain.suggestion.samples
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Fetches what a source offers, unfiltered: the data a suggestion is made from and checked against.
 *
 * Throws [AppError] when the source cannot be reached.
 */
class LoadSuggestionSourceUseCase @Inject constructor(
    private val githubRepository: GithubRepository,
    private val telegramRepository: TelegramRepository,
) {
    suspend operator fun invoke(app: TrackedApp): SourceSnapshot = when (val source = app.source) {
        is AppSource.GitHub -> SourceSnapshot.GitHub(githubRepository.getReleases(source.owner, source.repo))
        is AppSource.Telegram -> SourceSnapshot.Telegram(
            telegramRepository.getApkDocuments(source.chatId, source.topicId),
        )
    }
}

/**
 * Suggests filters for an app, from a file the user picked, their own words, or both.
 *
 * The order is cheapest-first. An example alone is solved without a model
 * ([ExampleFilterDeriver]) — instant, free, and available with AI off or on a phone without one.
 * Anything said in words, or an example the words cannot separate, goes to the model, whose every
 * reply is checked by running it ([ProposalVerifier]) and, when it fails, sent back with what was
 * wrong, up to [MAX_ATTEMPTS] times. Only a proposal that passes is ever offered; whether it is
 * what the user meant is theirs to judge, which is why the result is a suggestion and not an edit.
 *
 * Errors from the model or the network propagate as [AppError] and end the flow.
 */
class SuggestFiltersUseCase @Inject constructor(
    private val languageModel: LanguageModelGateway,
    private val deviceInfo: DeviceInfo,
) {
    operator fun invoke(request: SuggestionRequest): Flow<SuggestionStep> = flow {
        val app = request.app
        val current = FilterProposal.of(app)
        val samples = request.snapshot.samples(app)
        val abis = deviceInfo.supportedAbis
        val example = request.example
        val exampleGroup = example?.let { file -> samples.firstOrNull { group -> group.files.any { it.ref == file.ref } } }

        fun verify(proposal: FilterProposal): Verification =
            ProposalVerifier.verify(proposal, app, request.snapshot, example, abis)

        // ---- the example alone ----
        var startingPoint: FilterProposal? = null
        var exampleFailure: FailedAttempt? = null
        if (example != null && exampleGroup != null) {
            // Without the ABI first, so the filter also fits phones with another architecture.
            for (pinAbi in listOf(false, true)) {
                val derived = ExampleFilterDeriver.derive(example, exampleGroup, current, pinAbi) ?: continue
                val verification = verify(derived)
                if (verification.passed) {
                    if (request.needsOnlyExample) {
                        emit(SuggestionStep.Suggested(FilterSuggestion(derived, verification, explanation = null, backend = null)))
                        return@flow
                    }
                    startingPoint = derived
                    exampleFailure = null
                    break
                }
                if (startingPoint == null) startingPoint = derived
                exampleFailure = FailedAttempt(derived, verification.problems)
            }
        }

        // ---- the model ----
        val availability = languageModel.availability()
        if (availability !is AiAvailability.Available) {
            // An example the words could not separate, with no model to ask: say what failed.
            if (request.needsOnlyExample) {
                emit(SuggestionStep.NoWorkingFilter(exampleFailure))
                return@flow
            }
            throw (availability as AiAvailability.Unavailable).reason.toAppError()
        }
        if (availability.needsDownload) {
            languageModel.prepare().collect { emit(SuggestionStep.DownloadingModel(it)) }
        }

        // A starting point that failed comes with its problems, so the model starts from them.
        val attempts = listOfNotNull(exampleFailure).toMutableList()
        repeat(MAX_ATTEMPTS) { index ->
            emit(SuggestionStep.Generating(attempt = index + 1, maxAttempts = MAX_ATTEMPTS))
            val prompt = FilterPrompt.build(
                PromptContext(
                    kind = request.snapshot.kind,
                    samples = exampleGroup?.let { listOf(it) + (samples - it) } ?: samples,
                    current = current,
                    example = example,
                    startingPoint = startingPoint,
                    request = request.instructions,
                    rejected = request.rejected,
                    attempts = attempts,
                    language = request.language,
                    budget = if (availability.backend == AiBackend.OnDevice) PromptBudget.OnDevice else PromptBudget.Cloud,
                ),
            )
            val reply = languageModel.generate(prompt)
            val parsed = FilterPrompt.parse(reply, request.snapshot.kind, current)
            if (parsed == null) {
                attempts += FailedAttempt(null, listOf(ProposalProblem.Unreadable(reply.take(UNREADABLE_EXCERPT))))
                return@repeat
            }
            val verification = verify(parsed.proposal)
            if (verification.passed) {
                val suggestion = FilterSuggestion(parsed.proposal, verification, parsed.explanation, availability.backend)
                emit(SuggestionStep.Suggested(suggestion))
                return@flow
            }
            attempts += FailedAttempt(parsed.proposal, verification.problems)
        }
        emit(SuggestionStep.NoWorkingFilter(attempts.lastOrNull()))
    }

    private fun AiUnavailableReason.toAppError(): AppError.Ai = AppError.Ai(
        when (this) {
            AiUnavailableReason.Disabled -> AiFailure.Disabled
            AiUnavailableReason.Unsupported -> AiFailure.Unsupported
            AiUnavailableReason.MissingApiKey -> AiFailure.MissingApiKey
        },
    )

    companion object {
        /** Replies the model gets before the run gives up. Each costs a few seconds, or money. */
        const val MAX_ATTEMPTS = 3

        private const val UNREADABLE_EXCERPT = 300
    }
}

/** What the user asked for. */
data class SuggestionRequest(
    /** The details page's draft, current filters included. */
    val app: TrackedApp,
    val snapshot: SourceSnapshot,
    /** A file of the newest release or message that the user wants installed. */
    val example: SampleFile? = null,
    /** What the user wants, in their own words. */
    val instructions: String = "",
    /** Earlier suggestions of this session the user turned down, with what they said. */
    val rejected: List<RejectedSuggestion> = emptyList(),
    /** English name of the user's language, for the model's explanation. */
    val language: String = "English",
) {
    /** Nothing to say beyond the example: no model needed, unless the example is not enough. */
    val needsOnlyExample: Boolean get() = example != null && instructions.isBlank() && rejected.isEmpty()
}

/** Progress of one suggestion run. The last step is [Suggested] or [NoWorkingFilter]. */
sealed interface SuggestionStep {
    /** The on-device model is downloading; [bytes] so far. */
    data class DownloadingModel(val bytes: Long) : SuggestionStep

    /** Waiting for the model's reply number [attempt]. */
    data class Generating(val attempt: Int, val maxAttempts: Int) : SuggestionStep

    data class Suggested(val suggestion: FilterSuggestion) : SuggestionStep

    /** Every attempt failed the check. [last] says how the last one failed. */
    data class NoWorkingFilter(val last: FailedAttempt?) : SuggestionStep
}

/** A proposal that passed the check, with what it would change. */
data class FilterSuggestion(
    val proposal: FilterProposal,
    val verification: Verification,
    /** The model's one-line reason; null for a suggestion made from the example alone. */
    val explanation: String?,
    /** What made it; null when no model was involved. */
    val backend: AiBackend?,
)
