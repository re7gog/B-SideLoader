package dev.re7gog.b_sideloader.domain.installer

import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when a download or an install may run. Every install of a tracked app goes through
 * here, whoever started it, so these rules hold app-wide:
 *
 *  - **Downloads are limited per source.** One at a time from each source by default, up to
 *    [AppSettings.MAX_PARALLEL_DOWNLOADS_PER_SOURCE] with [AppSettings.parallelUpdates] on.
 *    Different sources never wait for each other — a GitHub download and a Telegram download
 *    compete for nothing — so downloads are always at least partly parallel.
 *  - **Installs run one at a time**, always: `PackageInstaller` sessions do not overlap sanely,
 *    and on the unprivileged path each one raises its own confirmation dialog.
 *  - **B-SideLoader's own update installs last.** Replacing the package kills this process, and
 *    with it every download still running — so it waits for the installs already in flight.
 *
 * The per-source limit is read live: turning the setting on lets waiting downloads start at once.
 */
@Singleton
class InstallScheduler @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private val downloads = AppSourceKind.entries.associateWith { MutableStateFlow(0) }
    private val installer = Mutex()

    /** Installs between [track]'s start and end, whatever phase they are in. */
    private val inFlight = MutableStateFlow(0)

    private val downloadLimit = settingsRepository.settings
        .map { if (it.parallelUpdates) AppSettings.MAX_PARALLEL_DOWNLOADS_PER_SOURCE else 1 }
        .distinctUntilChanged()

    /** Brackets one whole install, download to verdict, so a last-in-line install can wait for it. */
    suspend fun <T> track(block: suspend () -> T): T {
        inFlight.update { it + 1 }
        try {
            return block()
        } finally {
            inFlight.update { it - 1 }
        }
    }

    /** Runs [block] — a download from [source] — once that source has a free slot. */
    suspend fun <T> download(source: AppSourceKind, block: suspend () -> T): T {
        val active = downloads.getValue(source)
        acquire(active)
        try {
            return block()
        } finally {
            active.update { it - 1 }
        }
    }

    /**
     * Runs [block] — an install — once no other install is running.
     *
     * @param last wait first until this is the only install in flight (counting itself, so call it
     *   inside [track]). For B-SideLoader's own update; see the class comment.
     */
    suspend fun <T> install(last: Boolean = false, block: suspend () -> T): T {
        if (last) inFlight.first { it <= 1 }
        return installer.withLock { block() }
    }

    private suspend fun acquire(active: MutableStateFlow<Int>) {
        while (true) {
            val limit = downloadLimit.first()
            val current = active.value
            if (current < limit) {
                if (active.compareAndSet(current, current + 1)) return
            } else {
                combine(active, downloadLimit) { running, max -> running < max }.first { it }
            }
        }
    }
}
