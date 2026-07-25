package dev.re7gog.b_sideloader.data.installer.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
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
    ): InstallOutcome {
        val requestId = bus.newRequestId()
        var sessionId = INVALID_SESSION

        try {
            sessionId = packageInstaller.createSession(sessionParams())
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
            abandon(sessionId)
            return InstallOutcome.Failure(e)
        } catch (e: Throwable) {
            // Includes CancellationException: the session must go either way, and rethrowing
            // afterwards keeps cancellation propagating.
            abandon(sessionId)
            throw e
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
     * same request id afterwards.
     */
    private fun launchConfirmation(event: PackageInstallerEvent) {
        val confirmation = event.userAction ?: return
        runCatchingCancellable {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmation)
        }.onFailure { logger.e(TAG, it) { "Could not show the install confirmation" } }
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

    private fun abandon(sessionId: Int) {
        if (sessionId == INVALID_SESSION) return
        runCatchingCancellable { packageInstaller.abandonSession(sessionId) }
            .onFailure { logger.w(TAG, it) { "Could not abandon session $sessionId" } }
    }

    private companion object {
        const val TAG = "SessionInstall"
        const val WRITE_NAME = "b_sideloader_install"
        const val INVALID_SESSION = -1
    }
}
