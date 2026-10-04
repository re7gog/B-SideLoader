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

/**
 * One GitHub release, or one Telegram message — an album of several files counts as one — with
 * every APK it carries.
 *
 * The details page lists these rather than loose files, so the user sees each release or post as
 * a whole: its name and notes, the files the APK filter let through, the ones it left out, and
 * which one would actually be installed. Only groups the release/message filter accepts, and with
 * at least one file the APK filter accepts, are produced.
 */
data class CandidateGroup(
    /** The release name. Telegram messages have none. */
    val title: String?,
    /** Release notes or message caption, shared by every file in the group. */
    val notes: String?,
    /** Every APK in the group, in the order the release or post lists them. */
    val files: List<CandidateFile>,
) {
    /** The files that can be installed: those the APK filter lets through. */
    val candidates: List<UpdateCandidate>
        get() = files.mapNotNull { file -> file.candidate.takeIf { file.matchesFilter } }
}

/** One APK in a [CandidateGroup]. */
data class CandidateFile(
    val candidate: UpdateCandidate,
    /** False when the APK filter leaves it out; it is listed, but never installed. */
    val matchesFilter: Boolean,
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
