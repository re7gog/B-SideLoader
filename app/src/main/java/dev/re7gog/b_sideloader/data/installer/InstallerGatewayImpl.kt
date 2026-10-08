package dev.re7gog.b_sideloader.data.installer

import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.installer.privileged.PrivilegedApkInstallerFactory
import dev.re7gog.b_sideloader.data.installer.session.SessionApkInstaller
import dev.re7gog.b_sideloader.data.installer.session.SessionPreapprover
import dev.re7gog.b_sideloader.data.installer.session.UpdateFactsReader
import dev.re7gog.b_sideloader.data.installer.session.UserActionPolicy
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.DownloadProgress
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.DownloadedApk
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.PreapprovalSession
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramDownload
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks an installation backend per operation and turns it into a progress stream.
 *
 * The backend is resolved at call time (not injected once) because the user can switch installer
 * modes in settings while a details screen is open, and the next install should honour the new
 * choice without recreating anything.
 *
 * Downloading and installing are separate calls so that downloads can run in parallel while
 * installs stay one at a time; the gateway itself imposes neither, that is
 * [dev.re7gog.b_sideloader.domain.installer.InstallScheduler]'s job. Both phases go through a
 * file: HTTP downloads land in the cache via [HttpApkSource], Telegram ones in TDLib's own store.
 *
 * Each pipeline runs on [DispatcherProvider.io] via a single `flowOn`, which is what keeps the
 * flow-context invariant intact while still letting the blocking socket/session reads happen off
 * the main thread.
 */
@Singleton
class InstallerGatewayImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionInstaller: SessionApkInstaller,
    private val preapprover: SessionPreapprover,
    private val updateFacts: UpdateFactsReader,
    private val privilegedFactory: PrivilegedApkInstallerFactory,
    private val httpApkSource: HttpApkSource,
    private val telegramRepository: TelegramRepository,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : InstallerGateway {

    override fun download(source: DownloadRef): Flow<DownloadProgress> = flow {
        val apk = when (source) {
            is DownloadRef.Http -> downloadFromHttp(source)
            is DownloadRef.TelegramFile -> downloadFromTelegram(source)
        }
        emit(DownloadProgress.Downloaded(apk))
    }.catch { e ->
        // Upstream failures only — `catch` never sees what the collector throws.
        e.rethrowIfCancellation()
        if (e !is AppError) logger.e(TAG, e) { "Download failed" }
        emit(DownloadProgress.Failed(e as? AppError ?: AppError.Unexpected(e)))
    }.flowOn(dispatchers.io)

    override fun installDownloaded(
        apk: DownloadedApk,
        interactive: Boolean,
        preapproved: PreapprovalSession?,
    ): Flow<InstallProgress> = installFile(
        File(apk.path),
        fallbackSize = apk.sizeBytes,
        missing = "Downloaded file is missing",
        interactive = interactive,
        preapproved = preapproved,
    )

    override suspend fun requiresConfirmation(packageName: String): Boolean =
        withContext(dispatchers.io) {
            !settingsRepository.current().installerMode.isPrivileged &&
                UserActionPolicy.isRequired(updateFacts.read(packageName))
        }

    override suspend fun openPreapprovalSession(packageName: String): PreapprovalSession? =
        withContext(dispatchers.io) {
            preapprover.open(packageName, settingsRepository.current().installerMode)
        }

    override suspend fun requestPreapproval(session: PreapprovalSession): PreapprovalDecision =
        withContext(dispatchers.io) { preapprover.request(session) }

    override suspend fun abandon(session: PreapprovalSession) =
        withContext(dispatchers.io) { sessionInstaller.abandonSession(session.sessionId) }

    override fun installLocal(apk: LocalApk): Flow<InstallProgress> = flow {
        emit(InstallProgress.Preparing)
        // The user picked the file and is looking at the screen.
        emitAll(
            installFile(
                File(apk.path),
                fallbackSize = apk.sizeBytes,
                missing = "The staged APK is gone",
                interactive = true,
            )
        )
    }

    override suspend fun discard(apk: DownloadedApk) {
        if (apk.source !is DownloadRef.Http) return
        withContext(dispatchers.io) {
            val file = File(apk.path)
            if (httpApkSource.owns(file)) file.delete()
        }
    }

    override suspend fun uninstall(packageName: String): UninstallOutcome {
        val mode = settingsRepository.current().installerMode
        return try {
            if (mode.isPrivileged) {
                privilegedFactory.use(mode.usesDhizuku) { it.uninstall(packageName) }
            } else {
                sessionInstaller.uninstall(packageName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(TAG, e) { "Uninstall of $packageName failed" }
            UninstallOutcome.Failure(AppError.Unexpected(e))
        }
    }

    override suspend fun checkPrivilegedAccess(mode: InstallerMode): PrivilegedAccess {
        if (!mode.isPrivileged) return PrivilegedAccess.Granted(
            dev.re7gog.b_sideloader.domain.model.PrivilegedIdentity.Adb
        )
        return try {
            privilegedFactory.use(mode.usesDhizuku) { it.checkAccess() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.w(TAG, e) { "Privileged access check failed" }
            PrivilegedAccess.Unavailable(
                AppError.Privileged(
                    dev.re7gog.b_sideloader.domain.error.PrivilegedFailure.ServiceNotFound,
                    e,
                )
            )
        }
    }

    private suspend fun FlowCollector<DownloadProgress>.downloadFromHttp(
        source: DownloadRef.Http,
    ): DownloadedApk {
        val file = httpApkSource.download(source.url) { emit(DownloadProgress.Downloading(it)) }
        return DownloadedApk(path = file.absolutePath, sizeBytes = file.length(), source = source)
    }

    /**
     * TDLib pulls the file into its own store and reports progress as it goes, so a large APK does
     * not look frozen while it downloads. The copy is TDLib's, released through
     * [TelegramRepository] once the install is over.
     */
    private suspend fun FlowCollector<DownloadProgress>.downloadFromTelegram(
        source: DownloadRef.TelegramFile,
    ): DownloadedApk {
        var localPath: String? = null
        telegramRepository.downloadFile(source.fileId).collect { update ->
            when (update) {
                is TelegramDownload.Progress -> emit(DownloadProgress.Downloading(update.fraction))
                is TelegramDownload.Completed -> localPath = update.localPath
            }
        }
        val file = File(localPath ?: throw AppError.Storage("Telegram did not return a file"))
        if (!file.exists()) throw AppError.Storage("Downloaded file is missing")
        return DownloadedApk(
            path = file.absolutePath,
            sizeBytes = file.length().takeIf { it > 0 } ?: source.sizeBytes,
            source = source,
        )
    }

    /**
     * Streams [file] into the backend for the current mode — or into [preapproved], which this
     * then owns. [missing] explains a vanished file.
     */
    private fun installFile(
        file: File,
        fallbackSize: Long,
        missing: String,
        interactive: Boolean,
        preapproved: PreapprovalSession? = null,
    ): Flow<InstallProgress> =
        flow {
            val mode = settingsRepository.current().installerMode
            if (!file.exists()) {
                preapproved?.let { sessionInstaller.abandonSession(it.sessionId) }
                emit(InstallProgress.Finished(InstallOutcome.Failure(AppError.Storage(missing))))
                return@flow
            }
            val outcome = runInstall(mode, preapproved) { backend ->
                ApkPayload(
                    lengthBytes = file.length().takeIf { it > 0 } ?: fallbackSize,
                    stream = file.inputStream(),
                ).use { payload ->
                    backend.install(payload, interactive) { emit(InstallProgress.Staging(it)) }
                }
            }
            emit(InstallProgress.Finished(outcome))
        }.flowOn(dispatchers.io)

    /** Runs [block] against the backend for [mode], turning unexpected failures into an outcome. */
    private suspend fun runInstall(
        mode: InstallerMode,
        preapproved: PreapprovalSession? = null,
        block: suspend (ApkInstallerBackend) -> InstallOutcome,
    ): InstallOutcome = try {
        if (preapproved != null) {
            // The user approved exactly this session; honour that even if the mode changed since.
            block(sessionInstaller.into(preapproved.sessionId))
        } else if (mode.isPrivileged) {
            privilegedFactory.use(mode.usesDhizuku) { installer ->
                when (val access = installer.checkAccess()) {
                    is PrivilegedAccess.Granted -> block(installer)
                    is PrivilegedAccess.Unavailable -> InstallOutcome.Failure(access.error)
                }
            }
        } else {
            block(sessionInstaller)
        }
    } catch (e: Throwable) {
        // A failure before the session was even opened would otherwise leave it behind.
        preapproved?.let { sessionInstaller.abandonSession(it.sessionId) }
        e.rethrowIfCancellation()
        if (e is AppError) {
            InstallOutcome.Failure(e)
        } else {
            logger.e(TAG, e) { "Install failed" }
            InstallOutcome.Failure(AppError.Unexpected(e))
        }
    }

    private companion object {
        const val TAG = "Installer"
    }
}
