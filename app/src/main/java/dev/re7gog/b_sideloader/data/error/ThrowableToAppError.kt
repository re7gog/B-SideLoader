package dev.re7gog.b_sideloader.data.error

import dev.re7gog.b_sideloader.core.coroutines.rethrowIfCancellation
import dev.re7gog.b_sideloader.domain.error.AppError
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Translates the data layer's exception vocabulary into the domain's [AppError] hierarchy.
 *
 * Everything above the data layer sees only [AppError], which is what lets the UI render a
 * specific, actionable message ("GitHub rate limit reached, try again at 14:05") instead of the
 * `e.message ?: "error"` string soup the old code showed.
 *
 * Cancellation is never translated — it is rethrown, so a cancelled coroutine stays cancelled.
 */
fun Throwable.toAppError(): AppError {
    rethrowIfCancellation()
    return when (this) {
        is AppError -> this
        is HttpException -> toAppError()
        is SerializationException -> AppError.Unexpected(this)
        is IOException -> AppError.Network(this)
        else -> AppError.Unexpected(this)
    }
}

/**
 * Maps an HTTP status to a domain error.
 *
 * GitHub reports an exhausted quota as `403` (not `429`) with `X-RateLimit-Remaining: 0`, so the
 * headers have to be consulted before `403` can be called an authorization problem — otherwise a
 * rate-limited user is told their token is wrong.
 */
private fun HttpException.toAppError(): AppError {
    val response = response()
    val headers = response?.headers()
    val remaining = headers?.get(HEADER_RATE_REMAINING)?.toIntOrNull()
    val resetAt = headers?.get(HEADER_RATE_RESET)?.toLongOrNull()

    return when {
        code() == HttpURLConnection.HTTP_FORBIDDEN && remaining == 0 ->
            AppError.RateLimited(resetAt, this)

        code() == STATUS_TOO_MANY_REQUESTS -> AppError.RateLimited(resetAt, this)

        code() == HttpURLConnection.HTTP_UNAUTHORIZED ||
            code() == HttpURLConnection.HTTP_FORBIDDEN -> AppError.Unauthorized(this)

        code() == HttpURLConnection.HTTP_NOT_FOUND -> AppError.NotFound(this)

        else -> AppError.Http(code(), message(), this)
    }
}

private const val HEADER_RATE_REMAINING = "X-RateLimit-Remaining"
private const val HEADER_RATE_RESET = "X-RateLimit-Reset"
private const val STATUS_TOO_MANY_REQUESTS = 429

/**
 * Runs a data-layer call and rethrows any failure as an [AppError].
 *
 * Use at every boundary where the data layer calls something that throws its own exception types,
 * so a `SocketTimeoutException` can never reach a ViewModel.
 */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        throw e.toAppError()
    }
