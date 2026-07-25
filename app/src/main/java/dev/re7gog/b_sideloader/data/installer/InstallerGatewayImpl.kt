package dev.re7gog.b_sideloader.data.installer

import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.installer.privileged.PrivilegedApkInstallerFactory
import dev.re7gog.b_sideloader.data.installer.session.SessionApkInstaller
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramDownload
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * The whole pipeline runs on [DispatcherProvider.io] via a single `flowOn`, which is what keeps
 * the flow-context invariant intact while still letting the blocking socket/session reads happen
 * off the main thread.
 */
@Singleton
class InstallerGatewayImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sessionInstaller: SessionApkInstaller,
    private val privilegedFactory: PrivilegedApkInstallerFactory,
    private val httpApkSource: HttpApkSource,
    private val telegramRepository: TelegramRepository,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : InstallerGateway {

    override fun install(source: DownloadRef): Flow<InstallProgress> = flow {
        emit(InstallProgress.Preparing)
        val mode = settingsRepository.current().installerMode
        val outcome = when (source) {
            is DownloadRef.Http -> installFromHttp(source, mode)
            is DownloadRef.TelegramFile -> installFromTelegram(source, mode)
        }
        emit(InstallProgress.Finished(outcome))
    }.flowOn(dispatchers.io)

    override fun installLocal(apk: LocalApk): Flow<InstallProgress> = flow {
        emit(InstallProgress.Preparing)
        val mode = settingsRepository.current().installerMode
        val file = File(apk.path)
        if (!file.exists()) {
            emit(
                InstallProgress.Finished(
                    InstallOutcome.Failure(AppError.Storage("The staged APK is gone"))
                )
            )
            return@flow
        }
        val outcome = runInstall(mode) { backend ->
            ApkPayload(file.length(), file.inputStream()).use { payload ->
                backend.install(payload) { emit(InstallProgress.Staging(it)) }
            }
        }
        emit(InstallProgress.Finished(outcome))
    }.flowOn(dispatchers.io)

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

    /**
     * HTTP streams straight into the session, so there is only one measurable phase and it is
     * network-bound — reporting it as "downloading" rather than "staging" is what the user sees.
     */
    private suspend fun FlowCollector<InstallProgress>.installFromHttp(
        source: DownloadRef.Http,
        mode: InstallerMode,
    ): InstallOutcome = runInstall(mode) { backend ->
        httpApkSource.open(source.url).use { payload ->
            backend.install(payload) { emit(InstallProgress.Downloading(it)) }
        }
    }

    /**
     * Telegram has two distinct phases: TDLib pulls the file to disk, then we stream that file
     * into the session. Both are reported, so a large APK no longer looks frozen while TDLib
     * downloads it — the old code showed nothing at all until the download had finished.
     */
    private suspend fun FlowCollector<InstallProgress>.installFromTelegram(
        source: DownloadRef.TelegramFile,
        mode: InstallerMode,
    ): InstallOutcome {
        var localPath: String? = null
        telegramRepository.downloadFile(source.fileId).collect { update ->
            when (update) {
                is TelegramDownload.Progress -> emit(InstallProgress.Downloading(update.fraction))
                is TelegramDownload.Completed -> localPath = update.localPath
            }
        }
        val path = localPath
            ?: return InstallOutcome.Failure(AppError.Storage("Telegram did not return a file"))

        val file = File(path)
        if (!file.exists()) {
            return InstallOutcome.Failure(AppError.Storage("Downloaded file is missing"))
        }
        return runInstall(mode) { backend ->
            ApkPayload(
                lengthBytes = file.length().takeIf { it > 0 } ?: source.sizeBytes,
                stream = file.inputStream(),
            ).use { payload ->
                backend.install(payload) { emit(InstallProgress.Staging(it)) }
            }
        }
    }

    /** Runs [block] against the backend for [mode], turning unexpected failures into an outcome. */
    private suspend fun runInstall(
        mode: InstallerMode,
        block: suspend (ApkInstallerBackend) -> InstallOutcome,
    ): InstallOutcome = try {
        if (mode.isPrivileged) {
            privilegedFactory.use(mode.usesDhizuku) { installer ->
                when (val access = installer.checkAccess()) {
                    is PrivilegedAccess.Granted -> block(installer)
                    is PrivilegedAccess.Unavailable -> InstallOutcome.Failure(access.error)
                }
            }
        } else {
            block(sessionInstaller)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppError) {
        InstallOutcome.Failure(e)
    } catch (e: Throwable) {
        logger.e(TAG, e) { "Install failed" }
        InstallOutcome.Failure(AppError.Unexpected(e))
    }

    private companion object {
        const val TAG = "Installer"
    }
}
