package dev.re7gog.b_sideloader.ui.feature.apps

import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.usecase.TrackedAppStatus
import dev.re7gog.b_sideloader.domain.usecase.UpdateCheckOutcome

/**
 * The list's private record of what the last check found and what is installing right now.
 *
 * Kept as one value rather than three `MutableStateFlow`s so a row can never be seen as
 * "up to date" while its download progress is still ticking — every transition below moves the
 * verdict, the candidate and the progress together.
 */
internal data class UpdateBoard(
    val states: Map<Long, AppUpdateState> = emptyMap(),
    val candidates: Map<Long, UpdateCandidate> = emptyMap(),
    /** `null` for a phase with no measurable fraction (preparing, committing). */
    val progress: Map<Long, Float?> = emptyMap(),
    val isChecking: Boolean = false,
) {
    /**
     * Folds a completed check in.
     *
     * Rows that are mid-install keep their [AppUpdateState.Updating] state: the check that just
     * finished started before the install did, so its verdict is older than what is on screen.
     */
    fun withCheckResults(outcomes: List<UpdateCheckOutcome>): UpdateBoard {
        val nextStates = states.toMutableMap()
        val nextCandidates = candidates.toMutableMap()

        outcomes.forEach { outcome ->
            val id = outcome.app.id
            if (states[id] == AppUpdateState.Updating) return@forEach
            when {
                outcome.skipped -> {
                    nextStates -= id
                    nextCandidates -= id
                }

                outcome.error != null -> {
                    nextStates[id] = AppUpdateState.Failed
                    nextCandidates -= id
                }

                outcome.hasUpdate -> {
                    nextStates[id] = AppUpdateState.Available
                    outcome.check?.candidate?.let { nextCandidates[id] = it }
                }

                else -> {
                    nextStates[id] = AppUpdateState.UpToDate
                    nextCandidates -= id
                }
            }
        }
        return copy(states = nextStates, candidates = nextCandidates)
    }

    /**
     * [states], held against what the database says *now*.
     *
     * A verdict is as old as the check that produced it, while a row's version can move after that
     * check — an install from the details screen, or B-SideLoader's own row being reconciled. A row
     * whose stored version already is the candidate is up to date whatever the verdict said;
     * without this it would keep offering an update that has already happened until the next
     * refresh.
     */
    fun statesFor(apps: List<TrackedAppStatus>): Map<Long, AppUpdateState> {
        val settled = apps.filter { status ->
            val id = status.app.id
            states[id] == AppUpdateState.Available && candidates[id]?.version == status.app.version
        }
        if (settled.isEmpty()) return states
        return states + settled.associate { it.app.id to AppUpdateState.UpToDate }
    }

    fun starting(id: Long): UpdateBoard = copy(
        states = states + (id to AppUpdateState.Updating),
        progress = progress + (id to null),
    )

    fun progressing(id: Long, fraction: Float?): UpdateBoard =
        copy(progress = progress + (id to fraction))

    fun installed(id: Long): UpdateBoard = copy(
        states = states + (id to AppUpdateState.UpToDate),
        candidates = candidates - id,
        progress = progress - id,
    )

    /**
     * Puts a failed install back where it was: the candidate is still valid and still newer, so
     * the row must keep offering the retry rather than pretending the app is up to date.
     */
    fun failed(id: Long): UpdateBoard = copy(
        states = states + (id to if (id in candidates) AppUpdateState.Available else AppUpdateState.Failed),
        progress = progress - id,
    )
}
