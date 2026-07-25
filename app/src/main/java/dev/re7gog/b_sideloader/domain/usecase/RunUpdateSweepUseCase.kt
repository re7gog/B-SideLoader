package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import javax.inject.Inject

/** What the sweep is allowed to do. */
enum class SweepMode {
    /** Only report which apps have updates. */
    CheckOnly,

    /** Also download and install them. */
    CheckAndInstall,
    ;

    companion object {
        /**
         * Installing without a visible prompt needs either a privileged installer, or Android 12+
         * where a self-installed app can be updated silently. Anywhere else the sweep can only
         * notify, because a session commit would throw an install dialog at a user who is not
         * looking at the phone.
         */
        fun forEnvironment(privilegedInstaller: Boolean, silentSelfUpdates: Boolean): SweepMode =
            if (privilegedInstaller || silentSelfUpdates) CheckAndInstall else CheckOnly
    }
}

/** Progress callbacks, so a worker or service can keep its notification honest. */
sealed interface SweepProgress {
    data class Checking(val appName: String, val index: Int, val total: Int) : SweepProgress
    data class Installing(val appName: String, val fraction: Float?) : SweepProgress
}

/** What one sweep did. */
data class SweepReport(
    val checked: Int = 0,
    val withUpdates: List<String> = emptyList(),
    val installed: List<String> = emptyList(),
    val failed: List<FailedApp> = emptyList(),
) {
    val hasUpdates: Boolean get() = withUpdates.isNotEmpty()

    data class FailedApp(val appName: String, val error: AppError)
}

/**
 * Checks every tracked app and, when allowed, installs what is new.
 *
 * Failure of one app never aborts the sweep — a rate-limited repository or a channel the user left
 * must not stop the other twenty apps from updating — but cancellation always propagates, so
 * WorkManager stopping the worker actually stops the work.
 */
class RunUpdateSweepUseCase @Inject constructor(
    private val appsRepository: AppsRepository,
    private val settingsRepository: SettingsRepository,
    private val resolveUpdate: ResolveUpdateUseCase,
    private val installApp: InstallAppUseCase,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
) {
    suspend operator fun invoke(
        mode: SweepMode = SweepMode.CheckAndInstall,
        onProgress: suspend (SweepProgress) -> Unit = {},
    ): SweepReport {
        val settings = settingsRepository.current()
        val effectiveMode = when (mode) {
            SweepMode.CheckOnly -> SweepMode.CheckOnly
            SweepMode.CheckAndInstall -> SweepMode.forEnvironment(
                privilegedInstaller = settings.installerMode.isPrivileged,
                silentSelfUpdates = deviceInfo.supportsSilentSelfUpdates,
            )
        }

        val apps = appsRepository.getApps().filter { it.autoUpdate }
        var report = SweepReport()

        apps.forEachIndexed { index, app ->
            onProgress(SweepProgress.Checking(app.name, index, apps.size))
            report = try {
                sweepOne(app, effectiveMode, report, onProgress)
            } catch (e: Throwable) {
                e.rethrowIfCancellation()
                logger.w(TAG, e) { "Update check failed for ${app.name}" }
                report.copy(
                    checked = report.checked + 1,
                    failed = report.failed + SweepReport.FailedApp(app.name, e.asAppError()),
                )
            }
        }
        return report
    }

    private suspend fun sweepOne(
        app: TrackedApp,
        mode: SweepMode,
        report: SweepReport,
        onProgress: suspend (SweepProgress) -> Unit,
    ): SweepReport {
        val check = resolveUpdate(app)
        val checked = report.copy(checked = report.checked + 1)
        if (!check.hasUpdate) return checked

        val candidate = requireNotNull(check.candidate) // hasUpdate implies a candidate
        val withUpdate = checked.copy(withUpdates = checked.withUpdates + app.name)
        if (mode == SweepMode.CheckOnly) return withUpdate

        var failure: AppError? = null
        installApp(app, candidate).collect { event ->
            when (event) {
                is AppInstallEvent.Progress ->
                    onProgress(SweepProgress.Installing(app.name, event.progress.fraction))

                is AppInstallEvent.Completed -> Unit
                is AppInstallEvent.Failed -> failure = event.error
            }
        }
        return when (val error = failure) {
            null -> withUpdate.copy(installed = withUpdate.installed + app.name)
            else -> withUpdate.copy(failed = withUpdate.failed + SweepReport.FailedApp(app.name, error))
        }
    }

    private fun Throwable.asAppError(): AppError =
        this as? AppError ?: AppError.Unexpected(this)

    private companion object {
        const val TAG = "UpdateSweep"
    }
}
