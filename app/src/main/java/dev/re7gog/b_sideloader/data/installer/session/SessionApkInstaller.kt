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
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import javax.inject.Inject

/**
 * The unprivileged path: a standard `PackageInstaller` session that the system confirms with the
 * user (or, from Android 12 on, installs silently when this app is already the installer of
 * record for that package).
 *
 * Correctness details this replaces:
 *  - The session is now abandoned on *any* failure **and on cancellation**, so a user who backs
 *    out mid-download no longer leaves an orphan session holding disk space until reboot.
 *  - The result is awaited on a subscription established *before* the commit, and matched by
 *    request id, so it can neither be missed nor picked up by an unrelated screen.
 *
 * On Android 14+ a session can also be opened before the download and approved by the user up
 * front ([createSession], [requestPreapproval], then [installInto]), so the commit needs no dialog;
 * [SessionPreapprover] decides when that is worth it.
 */
class SessionApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bus: InstallEventBus,
    private val logger: Logger,
) : ApkInstallerBackend {

    private val packageInstaller: PackageInstaller
        get() = context.packageManager.packageInstaller

    override suspend fun install(
        payload: ApkPayload,
        onProgress: suspend (Float) -> Unit,
    ): InstallOutcome = installInto(createSession(), payload, onProgress)

    /** This installer, writing into the existing session [sessionId] instead of a new one. */
    fun into(sessionId: Int): ApkInstallerBackend = object : ApkInstallerBackend {
        override suspend fun install(payload: ApkPayload, onProgress: suspend (Float) -> Unit) =
            installInto(sessionId, payload, onProgress)

        override suspend fun uninstall(packageName: String) =
            this@SessionApkInstaller.uninstall(packageName)
    }

    /** A new session, configured like every install's. The caller owns it until [installInto]. */
    fun createSession(): Int = packageInstaller.createSession(sessionParams())

    /**
     * Writes [payload] into the existing session [sessionId] and commits it — the way into a
     * session the user pre-approved. Takes the session over: it is abandoned on any failure and on
     * cancellation, so the caller never has to.
     */
    suspend fun installInto(
        sessionId: Int,
        payload: ApkPayload,
        onProgress: suspend (Float) -> Unit,
    ): InstallOutcome {
        val requestId = bus.newRequestId()
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite(WRITE_NAME, 0, payload.lengthBytes).use { output ->
                    payload.copyInto(output, onProgress)
                    session.fsync(output)
                }
                return awaitResult(requestId) {
                    session.commit(commitIntent(requestId).intentSender)
                }
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
     * The system answers like a commit: a pending user action (the dialog, launched here) and then
     * a verdict, or a verdict straight away. Aborted means the user declined, and the system has
     * already abandoned the session. Anything else that is not success — blocked on this device,
     * a dialog that could not be shown, a failure — is [PreapprovalDecision.Unavailable], and the
     * caller installs the usual way instead.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun requestPreapproval(
        sessionId: Int,
        details: PackageInstaller.PreapprovalDetails,
    ): PreapprovalDecision {
        val requestId = bus.newRequestId()
        val verdict = try {
            bus.events
                .onSubscription {
                    packageInstaller.openSession(sessionId).use { session ->
                        session.requestUserPreapproval(details, commitIntent(requestId).intentSender)
                    }
                }
                .filter { it.requestId == requestId }
                .first { event ->
                    if (event.status != PackageInstaller.STATUS_PENDING_USER_ACTION) return@first true
                    // Unshown, nobody could ever answer it; give up rather than wait forever.
                    check(launchConfirmation(event)) { "The approval dialog could not be shown" }
                    false
                }
                .status
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

    override suspend fun uninstall(packageName: String): UninstallOutcome =
        awaitEvent(bus.newRequestId()) { requestId ->
            packageInstaller.uninstall(packageName, uninstallIntent(requestId).intentSender)
        }.toUninstallOutcome(packageName)

    /**
     * Subscribes first, then runs [start], then waits for this request's verdict.
     *
     * [onSubscription] is the piece that removes the race: it runs after the collector is
     * registered, so a result that arrives immediately — which happens on a silent install — is
     * still delivered.
     */
    private suspend fun awaitResult(requestId: Int, start: () -> Unit): InstallOutcome =
        awaitEvent(requestId) { start() }.toInstallOutcome()

    private suspend fun awaitEvent(
        requestId: Int,
        start: (Int) -> Unit,
    ): PackageInstallerEvent =
        bus.events
            .onSubscription { start(requestId) }
            .filter { it.requestId == requestId }
            .onEach { event ->
                if (event.status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    launchConfirmation(event)
                }
            }
            .first { it.status != PackageInstaller.STATUS_PENDING_USER_ACTION }

    /**
     * Shows the system's install/uninstall confirmation. `FLAG_ACTIVITY_NEW_TASK` is required
     * because this is started from a non-Activity context; the verdict still arrives through the
     * same request id afterwards. False when there was nothing to show or it could not be shown.
     */
    private fun launchConfirmation(event: PackageInstallerEvent): Boolean {
        val confirmation = event.userAction ?: return false
        return runCatchingCancellable {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmation)
        }.onFailure { logger.e(TAG, it) { "Could not show the install confirmation" } }.isSuccess
    }

    private fun sessionParams() = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_FULL_INSTALL
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Only honoured when this app is already the installer of record for the package;
            // otherwise the system still asks, which is exactly the intended behaviour.
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

    private companion object {
        const val TAG = "SessionInstall"
        const val WRITE_NAME = "b_sideloader_install"
    }
}
