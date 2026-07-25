package dev.re7gog.b_sideloader.domain.error

/**
 * Every failure the domain can report, as one closed hierarchy.
 *
 * It is an [Exception] subtype on purpose: repositories and use cases `throw` these, so a caller
 * that has nothing useful to do with a failure simply lets it propagate, while a caller that does
 * gets an exhaustive `when` instead of string-sniffing `e.message`. The UI layer turns an
 * [AppError] into localized text (see `ui/common/error/AppErrorText.kt`) — the domain never knows
 * about string resources.
 *
 * Data-layer implementations are responsible for translating their own exception vocabulary
 * (`IOException`, `HttpException`, `TdApi.Error`, ...) into one of these; see
 * `data/error/ThrowableToAppError.kt`.
 */
sealed class AppError(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** No usable connection, DNS failure, timeout, TLS failure. Retrying later may work. */
    class Network(cause: Throwable? = null) : AppError("Network unavailable", cause)

    /** Server answered with a non-2xx status that has no more specific meaning below. */
    class Http(val code: Int, val serverMessage: String? = null, cause: Throwable? = null) :
        AppError("HTTP $code${serverMessage?.let { ": $it" }.orEmpty()}", cause)

    /** GitHub rejected the credentials, or a token is required and missing. */
    class Unauthorized(cause: Throwable? = null) : AppError("Not authorized", cause)

    /**
     * GitHub's per-hour quota is spent. [resetAtEpochSeconds] is the `X-RateLimit-Reset` value
     * when the server sent one, so the UI can say *when* it will work again.
     */
    class RateLimited(val resetAtEpochSeconds: Long? = null, cause: Throwable? = null) :
        AppError("Rate limited", cause)

    /** The repository, release, chat or topic does not exist (any more). */
    class NotFound(cause: Throwable? = null) : AppError("Not found", cause)

    /** TDLib returned an error for a request. [code] is TDLib's own error code. */
    class Telegram(val code: Int, val reason: String, cause: Throwable? = null) :
        AppError("Telegram error $code: $reason", cause)

    /** A Telegram operation was attempted while signed out. */
    class TelegramNotAuthorized : AppError("Not signed in to Telegram")

    /** Nothing in the source passed the app's include/exclude filters. */
    class NoMatchingRelease : AppError("No release matches the current filters")

    /** Reading, writing or parsing a local file failed. */
    class Storage(message: String, cause: Throwable? = null) : AppError(message, cause)

    /** The APK could not be installed. [reason] separates the cases the UI treats differently. */
    class Install(val reason: InstallFailure, val detail: String? = null, cause: Throwable? = null) :
        AppError("Install failed (${reason.name})${detail?.let { ": $it" }.orEmpty()}", cause)

    /** The privileged (Shizuku/Dhizuku) backend is unusable. */
    class Privileged(val reason: PrivilegedFailure, cause: Throwable? = null) :
        AppError("Privileged installer unavailable (${reason.name})", cause)

    /** Anything not yet classified. Keeps the hierarchy exhaustive without losing the cause. */
    class Unexpected(cause: Throwable?) : AppError(cause?.message ?: "Unexpected error", cause)
}

enum class InstallFailure {
    /** User dismissed the system install/uninstall confirmation. */
    Aborted,

    /** Not enough free space. */
    Storage,

    /** Downloaded payload was empty or truncated. */
    BadPayload,

    /** Installing would downgrade the package; Android refuses this. */
    Downgrade,

    /** `PackageInstaller` reported a failure with no more specific mapping. */
    Rejected,
}

enum class PrivilegedFailure {
    /** Neither Shizuku/Sui nor Dhizuku is running. */
    ServiceNotFound,

    /** The user denied the permission prompt. */
    Denied,

    /** Shizuku is older than v11 and exposes no usable API. */
    OutdatedShizuku,

    /** Android 8.0 + ADB cannot register a uid observer, so silent installs cannot work. */
    UnsupportedOnThisAndroid,
}
