package dev.re7gog.b_sideloader.ui.common.error

import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.error.PrivilegedFailure
import dev.re7gog.b_sideloader.ui.common.text.UiText
import java.text.DateFormat
import java.util.Date

/**
 * The single place a domain failure becomes something a user can read.
 *
 * Exhaustive over [AppError], so adding a failure mode makes the compiler point here instead of
 * letting it fall through to a generic "error" toast — which is what the previous
 * `e.message ?: "error"` pattern did for every single failure.
 */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.Network -> UiText.of(R.string.error_network)

    is AppError.Http -> UiText.of(R.string.error_http, code)

    is AppError.Unauthorized -> UiText.of(R.string.error_unauthorized)

    is AppError.RateLimited -> resetAtEpochSeconds
        ?.let { UiText.of(R.string.error_rate_limited_until, formatTime(it)) }
        ?: UiText.of(R.string.error_rate_limited)

    is AppError.NotFound -> UiText.of(R.string.error_not_found)

    is AppError.Telegram -> UiText.of(R.string.error_telegram, reason)

    is AppError.TelegramNotAuthorized -> UiText.of(R.string.error_telegram_signed_out)

    is AppError.NoMatchingRelease -> UiText.of(R.string.error_no_release_matches)

    is AppError.Storage -> UiText.Raw(message ?: "")

    is AppError.Install -> when (reason) {
        InstallFailure.Aborted -> UiText.of(R.string.error_install_aborted)
        InstallFailure.Storage -> UiText.of(R.string.error_install_storage)
        InstallFailure.BadPayload -> UiText.of(R.string.error_install_bad_payload)
        InstallFailure.Downgrade -> UiText.of(R.string.error_install_downgrade)
        InstallFailure.Rejected -> detail
            ?.let { UiText.of(R.string.error_install_rejected_detail, it) }
            ?: UiText.of(R.string.error_install_rejected)
    }

    is AppError.Privileged -> when (reason) {
        PrivilegedFailure.ServiceNotFound -> UiText.of(R.string.error_privileged_not_found)
        PrivilegedFailure.Denied -> UiText.of(R.string.error_privileged_denied)
        PrivilegedFailure.OutdatedShizuku -> UiText.of(R.string.error_privileged_outdated)
        PrivilegedFailure.UnsupportedOnThisAndroid -> UiText.of(R.string.error_privileged_unsupported)
    }

    is AppError.Unexpected -> message
        ?.takeIf { it.isNotBlank() }
        ?.let { UiText.Raw(it) }
        ?: UiText.of(R.string.error_unexpected)
}

/** Any throwable, including the ones that never made it into [AppError]. */
fun Throwable.toUiText(): UiText =
    (this as? AppError)?.toUiText() ?: UiText.of(R.string.error_unexpected)

private fun formatTime(epochSeconds: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochSeconds * MILLIS_PER_SECOND))

private const val MILLIS_PER_SECOND = 1_000L
