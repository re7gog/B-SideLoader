package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.ApplicationScope
import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Which install a progress entry or an [InstallResult] belongs to. */
sealed interface InstallKey {

    /** A saved app: at most one install per row, whichever screen started it. */
    data class App(val appId: Long) : InstallKey

    /**
     * An app opened from search, which has no row until its install saves one. Only the page that
     * started the install knows this key; the [InstallResult.Installed] it gets back carries the
     * row that was created, and from then on the app is an [App] like any other.
     */
    data class Draft(val ticket: Long) : InstallKey
}

/** How an install that went through [InstallCoordinator] ended. */
sealed interface InstallResult {
    val key: InstallKey

    /** Installed and persisted; see [AppInstallEvent.Completed]. */
    data class Installed(override val key: InstallKey, val app: TrackedApp) : InstallResult

    data class Failed(override val key: InstallKey, val app: TrackedApp, val error: AppError) :
        InstallResult
}

/**
 * The one place installs of tracked apps are started from — by the UI and by the background
 * sweep — and the one place their progress is read from.
 *
 * The apps list and the details page used to each run [InstallAppUseCase] in their own
 * `viewModelScope`, and the sweep in its worker, so none of them could see an install another had
 * started: "Update" on the list showed nothing on the app's page and the other way round, leaving
 * the details page cancelled its install outright, and a background update could run alongside
 * one the user had just started. Here every install is published per [InstallKey], so every
 * screen showing that app shows the same bar, whoever asked for it.
 *
 * Two ways in, one bookkeeping:
 *  - [install] is for screens: it runs in the application scope and returns at once, so the
 *    install outlives whichever screen asked for it.
 *  - [installAndAwait] is for the sweep: it runs in the caller's coroutine and returns the result,
 *    so cancelling the caller — WorkManager stopping the worker — still stops the install.
 *
 * When each install may download and when it may install is [InstallScheduler]'s call, applied
 * inside [InstallAppUseCase]: downloads run in parallel within per-source limits, installs one at
 * a time. A waiting install is reported as [InstallProgress.Queued]. What this class adds is that
 * an app is installed at most once at a time: [install] ignores an app already queued, and
 * [installAndAwait] waits for that install's result instead of starting another.
 *
 * Ordering guarantee: a screen collecting both streams handles an install's [InstallResult]
 * before it sees the install leave [installs], so it never shows "not installing any more" over
 * the state from before the install — no "Update" button flashing back for a frame. Emitting the
 * result first is not enough for that on its own: a [StateFlow] collector already scheduled for a
 * progress tick reads whatever the map holds when it finally runs. Hence [results] has no buffer,
 * and the entry is removed only once every subscriber has taken the result.
 */
@Singleton
class InstallCoordinator @Inject constructor(
    private val installApp: InstallAppUseCase,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val _installs = MutableStateFlow<Map<InstallKey, InstallProgress>>(emptyMap())

    /** Every install queued or running, with its latest progress. Finished ones are removed. */
    val installs: StateFlow<Map<InstallKey, InstallProgress>> = _installs.asStateFlow()

    /** Unbuffered on purpose; see the ordering guarantee above. */
    private val _results = MutableSharedFlow<InstallResult>()

    /**
     * Not replayed: a screen that was not there when an install ended has nothing to update.
     *
     * The install waits for every subscriber to take its result, so collect it without suspending
     * for long.
     */
    val results: SharedFlow<InstallResult> = _results.asSharedFlow()

    /**
     * How each install queued or running will end: completed with its result, or with null if it
     * was cancelled. Its keys are always exactly those of [installs]; both change under this lock.
     */
    private val endings = HashMap<InstallKey, CompletableDeferred<InstallResult?>>()

    private val tickets = AtomicLong()

    /** The key a saved app's install is reported under, or null for an app with no row yet. */
    fun keyOf(app: TrackedApp): InstallKey.App? = if (app.isSaved) InstallKey.App(app.id) else null

    /**
     * Queues [candidate] for [app] and returns the key its progress and result are reported under.
     *
     * Returns null, and queues nothing, when that app is already queued or installing — tapping
     * "Update" on the list and then on the app's page must not install it twice.
     */
    fun install(app: TrackedApp, candidate: UpdateCandidate): InstallKey? {
        val key = newKey(app)
        val claim = claim(key)
        if (!claim.isNew) return null
        scope.launch { run(key, app, candidate, claim.ending) }
        return key
    }

    /**
     * Installs [candidate] for [app] in the caller's coroutine and returns how it ended, reporting
     * progress to [onProgress] as well as to [installs].
     *
     * If that app is already queued or installing — the user started it a moment ago — this
     * installs nothing and waits for that install instead, forwarding its progress. The result
     * is null only when such an install was cancelled before it ended. Cancelling the caller
     * cancels an install it started, but never one it was merely waiting for.
     */
    suspend fun installAndAwait(
        app: TrackedApp,
        candidate: UpdateCandidate,
        onProgress: suspend (InstallProgress) -> Unit = {},
    ): InstallResult? {
        val key = newKey(app)
        val claim = claim(key)
        return if (claim.isNew) {
            run(key, app, candidate, claim.ending, onProgress)
        } else {
            awaitOther(key, claim.ending, onProgress)
        }
    }

    private fun newKey(app: TrackedApp): InstallKey =
        keyOf(app) ?: InstallKey.Draft(tickets.incrementAndGet())

    /** Registers [key] as queued, or hands back the ending of the install that already is. */
    private fun claim(key: InstallKey): Claim = synchronized(endings) {
        endings[key]?.let { return Claim(it, isNew = false) }
        val ending = CompletableDeferred<InstallResult?>()
        endings[key] = ending
        _installs.update { it + (key to InstallProgress.Queued) }
        Claim(ending, isNew = true)
    }

    private suspend fun run(
        key: InstallKey,
        app: TrackedApp,
        candidate: UpdateCandidate,
        ending: CompletableDeferred<InstallResult?>,
        onProgress: suspend (InstallProgress) -> Unit = {},
    ): InstallResult? {
        var result: InstallResult? = null
        try {
            onProgress(InstallProgress.Queued)
            installApp(app, candidate).collect { event ->
                when (event) {
                    is AppInstallEvent.Progress -> {
                        _installs.update { it + (key to event.progress) }
                        onProgress(event.progress)
                    }

                    is AppInstallEvent.Completed ->
                        result = InstallResult.Installed(key, event.app).also { _results.emit(it) }

                    is AppInstallEvent.Failed ->
                        result = InstallResult.Failed(key, app, event.error).also { _results.emit(it) }
                }
            }
        } catch (e: Throwable) {
            e.rethrowIfCancellation()
            logger.w(TAG, e) { "Install failed for ${app.name}" }
            val error = e as? AppError ?: AppError.Unexpected(e)
            result = InstallResult.Failed(key, app, error).also { _results.emit(it) }
        } finally {
            synchronized(endings) {
                endings -= key
                _installs.update { it - key }
            }
            ending.complete(result)
        }
        return result
    }

    private suspend fun awaitOther(
        key: InstallKey,
        ending: CompletableDeferred<InstallResult?>,
        onProgress: suspend (InstallProgress) -> Unit,
    ): InstallResult? = coroutineScope {
        val forwarding = launch {
            installs.mapNotNull { it[key] }.distinctUntilChanged().collect(onProgress)
        }
        ending.await().also { forwarding.cancel() }
    }

    private class Claim(val ending: CompletableDeferred<InstallResult?>, val isNew: Boolean)

    private companion object {
        const val TAG = "InstallCoordinator"
    }
}
