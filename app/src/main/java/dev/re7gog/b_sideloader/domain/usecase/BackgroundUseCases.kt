package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.background.BackgroundHealth
import dev.re7gog.b_sideloader.domain.background.BackgroundRestrictions
import dev.re7gog.b_sideloader.domain.background.BackgroundWorkScheduler
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Brings scheduled background work in line with the stored settings.
 *
 * Called from `Application.onCreate`, from `BOOT_COMPLETED`, and after every settings change that
 * affects scheduling — so a toggle always takes effect immediately, rather than at the next cold
 * start as it used to.
 */
class SyncBackgroundWorkUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: BackgroundWorkScheduler,
) {
    suspend operator fun invoke() {
        scheduler.sync(settingsRepository.current())
    }
}

/**
 * Reads everything that could stop background updates from running, for the "Background updates"
 * settings screen.
 */
class ObserveBackgroundHealthUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val restrictions: BackgroundRestrictions,
) {
    /**
     * Deliberately a suspending snapshot rather than a `Flow`: none of the underlying system
     * states are observable, so the screen re-reads them on resume — which is exactly when the
     * user comes back from the settings activity that changed them.
     */
    suspend operator fun invoke(): BackgroundHealth {
        val settings = settingsRepository.current()
        return BackgroundHealth(
            vendor = restrictions.vendor,
            mode = settings.backgroundMode,
            ignoresBatteryOptimizations = restrictions.isIgnoringBatteryOptimizations(),
            isBackgroundRestricted = restrictions.isBackgroundRestricted(),
            notificationsEnabled = restrictions.areNotificationsEnabled(),
            autoStartSettingsAvailable = restrictions.hasAutoStartSettings(),
        )
    }
}
