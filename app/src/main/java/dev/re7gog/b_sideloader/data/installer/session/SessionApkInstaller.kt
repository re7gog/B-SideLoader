package dev.re7gog.b_sideloader.data.installer.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.device.AppVisibility
import dev.re7gog.b_sideloader.data.device.Visibility
import dev.re7gog.b_sideloader.data.installer.ApkInstallerBackend
import dev.re7gog.b_sideloader.data.installer.ApkPayload
import dev.re7gog.b_sideloader.data.installer.InstallEventBus
import dev.re7gog.b_sideloader.data.installer.InstallResultReceiver
import dev.re7gog.b_sideloader.data.installer.PackageInstallerEvent
import dev.re7gog.b_sideloader.data.installer.UninstallResultReceiver
import dev.re7gog.b_sideloader.data.installer.copyInto
import dev.re7gog.b_sideloader.data.installer.toInstallOutcome
import dev.re7gog.b_sideloader.data.installer.toUninstallOutcome
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The unprivileged path: a standard `PackageInstaller` session that the system confirms with the
 * user — or, from Android 12 on, installs silently when this app may update the package without
 * asking (see [UserActionPolicy]).
 *
 * Correctness details this replaces:
 *  - The session is now abandoned on *any* failure **and on cancellation**, so a user who backs
 *    out mid-download no longer leaves an orphan session holding disk space until reboot.
 *  - The result is awaited on a subscription established *before* the commit, and matched by
 *    request id, so it can neither be missed nor picked up by an unrelated screen.
 *  - Nothing waits forever for an answer nobody can give. The system's confirmation is an
 *    activity this app starts, and Android quietly drops that start while the app is out of sight:
 *    an install committed from the background used to wait for a verdict that never came, holding
 *    the one install slot — and every install queued behind it — for good. See [askUser].
 *
 * On Android 14+ a session can also be opened before the download and approved by the user up
 * front ([createSession], [requestPreapproval], then [installInto]), so the commit needs no dialog;
 * [SessionPreapprover] decides when that is worth it.
 */
class SessionApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bus: InstallEventBus,
    private val visibility: AppVisibility,
    private val logger: Logger,
) : ApkInstallerBackend {

    private val packageInstaller: PackageInstaller
        get() = context.packageManager.packageInstaller

    override suspend fun install(
        payload: ApkPayload,
        interactive: Boolean,
        onProgress: suspend (Float) -> Unit,
    ): InstallOutcome = installInto(createSession(), payload, interactive, onProgress)

    /** This installer, writing into the existing session [sessionId] instead of a new one. */
    fun into(sessionId: Int): ApkInstallerBackend = object : ApkInstallerBackend {
        override suspend fun install(
            payload: ApkPayload,
            interactive: Boolean,
            onProgress: suspend (Float) -> Unit,
        ) = installInto(sessionId, payload, interactive, onProgress)

        override suspend fun uninstall(packageName: String) =
            this@SessionApkInstaller.uninstall(packageName)
    }

    /** A new session, configured like every install's. The caller owns it until [installInto]. */
    fun createSession(): Int = packageInstaller.createSession(sessionParams())

    /**
     * Writes [payload] into the existing session [sessionId] and commits it — the way into a
     * session the user pre-approved. Takes the session over: it is abandoned on any failure, on
     * cancellation and when the system asks a question nobody answers, so the caller never has to.
     *
     * @param interactive whether the user may be asked to confirm. When not, a commit the system
     *   will not take without asking is abandoned at once as [InstallOutcome.NeedsConfirmation].
     */
    suspend fun installInto(
        sessionId: Int,
        payload: ApkPayload,
        interactive: Boolean,
        onProgress: suspend (Float) -> Unit,
    ): InstallOutcome {
        val requestId = bus.newRequestId()
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite(WRITE_NAME, 0, payload.lengthBytes).use { output ->
                    payload.copyInto(output, onProgress)
                    session.fsync(output)
                }
                val verdict = awaitVerdict(requestId, interactive) {
                    session.commit(commitIntent(requestId).intentSender)
                }
                if (verdict == null) {
                    logger.i(TAG) { "Session $sessionId needs a confirmation nobody can give; dropping it" }
                    abandonSession(sessionId)
                    return InstallOutcome.NeedsConfirmation
                }
                return verdict.toInstallOutcome()
            }
        } catch (e: AppError) {
            abandonSession(sessionId)
            return InstallOutcome.Failure(e)
        } catch (e: Throwable) {
            // Includes CancellationException: the session must go either way, and rethrowing
            // afterwards keeps cancellation propagating.
            abandonSession(sessionId)
            throw e
        }
    }

    /**
     * Asks the user to approve installing into [sessionId] before anything is written to it, and
     * waits for the answer.
     *
     * The system answers like a commit: a pending user action (the dialog, shown by [askUser])
     * and then a verdict, or a verdict straight away. Aborted means the user declined, and the
     * system has already abandoned the session. Anything else that is not success — blocked on
     * this device, a dialog that could not be shown or was never answered, a failure — is
     * [PreapprovalDecision.Unavailable], and the caller installs the usual way instead.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun requestPreapproval(
        sessionId: Int,
        details: PackageInstaller.PreapprovalDetails,
    ): PreapprovalDecision {
        val requestId = bus.newRequestId()
        val verdict = try {
            awaitVerdict(requestId, interactive = true) {
                packageInstaller.openSession(sessionId).use { session ->
                    session.requestUserPreapproval(details, commitIntent(requestId).intentSender)
                }
            }?.status
        } catch (e: Throwable) {
            e.rethrowIfCancellation()
            logger.w(TAG, e) { "Could not ask for pre-approval of session $sessionId" }
            return PreapprovalDecision.Unavailable
        }
        logger.d(TAG) { "pre-approval of session $sessionId: status=$verdict" }
        return when (verdict) {
            PackageInstaller.STATUS_SUCCESS -> PreapprovalDecision.Approved
            PackageInstaller.STATUS_FAILURE_ABORTED -> PreapprovalDecision.Declined
            else -> PreapprovalDecision.Unavailable
        }
    }

    /** Always started by the user, so the system's confirmation is shown like an install's. */
    override suspend fun uninstall(packageName: String): UninstallOutcome {
        val requestId = bus.newRequestId()
        val verdict = awaitVerdict(requestId, interactive = true) {
            packageInstaller.uninstall(packageName, uninstallIntent(requestId).intentSender)
        } ?: return UninstallOutcome.Failure(AppError.Install(InstallFailure.Aborted, "Not confirmed"))
        return verdict.toUninstallOutcome(packageName)
    }

    /**
     * Runs [start], then waits for this request's verdict, asking the user whatever the system
     * asks on the way when [interactive]. Null when the system wants an answer nobody gave: not
     * [interactive], or [askUser] gave up.
     *
     * The subscription is registered before [start] runs, which removes the race: a result that
     * arrives immediately — which happens on a silent install — is still delivered.
     */
    private suspend fun awaitVerdict(
        requestId: Int,
        interactive: Boolean,
        start: () -> Unit,
    ): PackageInstallerEvent? = coroutineScope {
        val events = Channel<PackageInstallerEvent>(Channel.UNLIMITED)
        val subscribed = CompletableDeferred<Unit>()
        val listening = launch {
            bus.events
                .onSubscription { subscribed.complete(Unit) }
                .filter { it.requestId == requestId }
                .collect { events.send(it) }
        }
        try {
            subscribed.await()
            start()
            var event = events.receive()
            while (event.status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                if (!interactive) return@coroutineScope null
                event = askUser(event, events) ?: return@coroutineScope null
            }
            event
        } finally {
            listening.cancel()
        }
    }

    /**
     * Shows the system's question carried by [pending] and returns what comes next on [events]:
     * the verdict, or another question. Null when the user could not be asked — there was nothing
     * to show, it could not be shown, or they stayed away for [UNANSWERED_TIMEOUT].
     *
     * Three things stand between showing the question and an answer:
     *  - **The app is out of sight.** Android drops the activity start without a word, so it waits
     *    until the app can be seen — the user started this, left during the download, and gets
     *    the question when they come back.
     *  - **The user left the question unanswered**, say with Home. The system's dialog may live in
     *    its own task, out of recents, so nothing would bring it back; when the user returns to
     *    this app and no verdict follows within [RETURN_GRACE], it is shown again.
     *  - **Nobody ever answers.** After [UNANSWERED_TIMEOUT] without a verdict it gives up, so the
     *    install slot is free again for everyone queued behind it.
     */
    private suspend fun askUser(
        pending: PackageInstallerEvent,
        events: ReceiveChannel<PackageInstallerEvent>,
    ): PackageInstallerEvent? {
        val question = pending.userAction ?: return null
        while (true) {
            withTimeoutOrNull(UNANSWERED_TIMEOUT) { visibility.state.first { it != Visibility.Hidden } }
                ?: return null
            if (!launchConfirmation(question)) return null
            val reply = withTimeoutOrNull(UNANSWERED_TIMEOUT) {
                coroutineScope {
                    val cameBack = async { leaveAndComeBack() }
                    select<Reply> {
                        events.onReceive { Reply.Verdict(it) }
                        cameBack.onAwait { Reply.CameBack }
                    }.also { coroutineContext.cancelChildren() }
                }
            }
            when (reply) {
                null -> return null
                is Reply.Verdict -> return reply.event
                Reply.CameBack -> {
                    // The verdict of an answer given just before coming back may still be on its way.
                    withTimeoutOrNull(RETURN_GRACE) { events.receive() }?.let { return it }
                    logger.d(TAG) { "Back without an answer; asking again" }
                }
            }
        }
    }

    /** What ends a wait for an answer, short of the time running out. */
    private sealed interface Reply {
        data class Verdict(val event: PackageInstallerEvent) : Reply

        /** The user left with the question unanswered, and is now back in the app. */
        data object CameBack : Reply
    }

    /** Suspends until the user has left this app and come back to it, in front. */
    private suspend fun leaveAndComeBack() {
        visibility.state.first { it == Visibility.Hidden }
        visibility.state.first { it == Visibility.InFront }
    }

    /**
     * Shows the system's install/uninstall confirmation. `FLAG_ACTIVITY_NEW_TASK` is required
     * because this is started from a non-Activity context; the verdict still arrives through the
     * same request id afterwards. False when it could not be shown.
     */
    private fun launchConfirmation(confirmation: Intent): Boolean =
        runCatchingCancellable {
            context.startActivity(Intent(confirmation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { logger.e(TAG, it) { "Could not show the install confirmation" } }.isSuccess

    private fun sessionParams() = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_FULL_INSTALL
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Only honoured where UserActionPolicy says so; otherwise the system still asks, which
            // is exactly the intended behaviour.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
    }

    private fun commitIntent(requestId: Int): PendingIntent =
        resultIntent(requestId, InstallResultReceiver::class.java, InstallResultReceiver.ACTION_INSTALL_RESULT)

    private fun uninstallIntent(requestId: Int): PendingIntent =
        resultIntent(requestId, UninstallResultReceiver::class.java, UninstallResultReceiver.ACTION_UNINSTALL_RESULT)

    private fun resultIntent(requestId: Int, receiver: Class<*>, action: String): PendingIntent {
        val intent = Intent(context, receiver).apply {
            this.action = action
            putExtra(InstallResultReceiver.EXTRA_REQUEST_ID, requestId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Distinct request codes, otherwise FLAG_UPDATE_CURRENT would rewrite the extras of a
            // still-pending intent belonging to another install.
            requestId,
            intent,
            // MUTABLE: the system fills in EXTRA_STATUS and friends on this intent.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Drops a session. Quietly a no-op when the system already dropped or sealed it. */
    fun abandonSession(sessionId: Int) {
        runCatchingCancellable { packageInstaller.abandonSession(sessionId) }
            .onFailure { logger.d(TAG) { "Session $sessionId was not abandoned: ${it.message}" } }
    }

    internal companion object {
        private const val TAG = "SessionInstall"
        private const val WRITE_NAME = "b_sideloader_install"

        /**
         * How long a question waits for the app to come into sight, and then for an answer. Long
         * enough for anyone actually deciding; short enough that a forgotten one frees the slot.
         */
        val UNANSWERED_TIMEOUT = 10.minutes

        /** How long a verdict may take to arrive once the user is back in the app. */
        val RETURN_GRACE = 2.seconds
    }
}
