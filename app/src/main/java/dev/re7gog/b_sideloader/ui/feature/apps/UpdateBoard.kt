package dev.re7gog.b_sideloader.ui.feature.apps

import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.usecase.TrackedAppStatus
import dev.re7gog.b_sideloader.domain.usecase.UpdateCheckOutcome

/**
 * The list's private record of what the last check found.
 *
 * Kept as one value so a row's verdict and its candidate always move together. What is installing
 * right now is deliberately *not* here: that belongs to the app-wide
 * [dev.re7gog.b_sideloader.domain.usecase.InstallCoordinator], so an install started from the
 * details page shows on the list too. A row that is installing renders as
 * [AppUpdateState.Updating] whatever this board says, and falls back to its verdict here — still
 * offering the retry — if the install fails.
 */
internal data class UpdateBoard(
    val states: Map<Long, AppUpdateState> = emptyMap(),
    val candidates: Map<Long, UpdateCandidate> = emptyMap(),
    val isChecking: Boolean = false,
) {
    /**
     * Folds a completed check in.
     *
     * Rows that are mid-install take the verdict too. It cannot show while the install runs, and
     * it is what the row should fall back to if the install fails; if it succeeds, [installed]
     * replaces it.
     */
    fun withCheckResults(outcomes: List<UpdateCheckOutcome>): UpdateBoard {
        val nextStates = states.toMutableMap()
        val nextCandidates = candidates.toMutableMap()

        outcomes.forEach { outcome ->
            val id = outcome.app.id
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

    /** An install of this row finished, from this screen or any other. */
    fun installed(id: Long): UpdateBoard = copy(
        states = states + (id to AppUpdateState.UpToDate),
        candidates = candidates - id,
    )
}
