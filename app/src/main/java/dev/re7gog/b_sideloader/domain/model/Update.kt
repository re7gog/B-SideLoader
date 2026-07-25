package dev.re7gog.b_sideloader.domain.model

/**
 * A downloadable APK that a source offers for an app, already filtered and architecture-matched.
 */
data class UpdateCandidate(
    val version: AppVersion,
    val download: DownloadRef,
    /** Asset / file name, shown in the UI and used for ABI matching. */
    val fileName: String,
    val sizeBytes: Long? = null,
    /** Release notes or message caption, when the source provides one. */
    val notes: String? = null,
)

/** How to fetch a candidate's bytes. */
sealed interface DownloadRef {
    /** A plain HTTPS download (GitHub release asset). */
    data class Http(val url: String) : DownloadRef

    /** A file held by TDLib, fetched through the Telegram client. */
    data class TelegramFile(val fileId: Int, val sizeBytes: Long) : DownloadRef
}

/** Whether a resolved candidate is actually newer than what is installed. */
enum class UpdateStatus {
    /** Installed version equals the candidate. */
    UpToDate,

    /** A different (newer) candidate exists. */
    UpdateAvailable,

    /** The app has never been installed from here, so any candidate is a first install. */
    NotInstalled,

    /** Nothing passed the filters. */
    NoCandidate,
}

/** Result of resolving one app against its source. */
data class UpdateCheck(
    val app: TrackedApp,
    val candidate: UpdateCandidate?,
) {
    val status: UpdateStatus
        get() = when {
            candidate == null -> UpdateStatus.NoCandidate
            !app.version.isKnown -> UpdateStatus.NotInstalled
            app.version == candidate.version -> UpdateStatus.UpToDate
            else -> UpdateStatus.UpdateAvailable
        }

    val hasUpdate: Boolean get() = status == UpdateStatus.UpdateAvailable
}
