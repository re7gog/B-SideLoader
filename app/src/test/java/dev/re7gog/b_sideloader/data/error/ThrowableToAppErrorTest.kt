package dev.re7gog.b_sideloader.data.error

import dev.re7gog.b_sideloader.domain.error.AppError
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ThrowableToAppErrorTest {

    @Test
    fun `io failures become a network error`() {
        assertTrue(UnknownHostException("no dns").toAppError() is AppError.Network)
        assertTrue(SocketTimeoutException("timeout").toAppError() is AppError.Network)
        assertTrue(IOException("reset").toAppError() is AppError.Network)
    }

    @Test
    fun `404 becomes NotFound`() {
        assertTrue(httpException(404).toAppError() is AppError.NotFound)
    }

    @Test
    fun `401 becomes Unauthorized`() {
        assertTrue(httpException(401).toAppError() is AppError.Unauthorized)
    }

    /**
     * GitHub reports an exhausted quota as 403 with `X-RateLimit-Remaining: 0`. Without checking
     * that header the user is wrongly told their token is invalid.
     */
    @Test
    fun `403 with an exhausted quota becomes RateLimited`() {
        val error = httpException(
            code = 403,
            headers = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1700000000"),
        ).toAppError()

        assertTrue(error is AppError.RateLimited)
        assertEquals(1_700_000_000L, (error as AppError.RateLimited).resetAtEpochSeconds)
    }

    @Test
    fun `403 with quota remaining becomes Unauthorized`() {
        val error = httpException(403, mapOf("X-RateLimit-Remaining" to "4999")).toAppError()

        assertTrue(error is AppError.Unauthorized)
    }

    @Test
    fun `429 becomes RateLimited`() {
        assertTrue(httpException(429).toAppError() is AppError.RateLimited)
    }

    @Test
    fun `other statuses keep their code`() {
        val error = httpException(503).toAppError()

        assertTrue(error is AppError.Http)
        assertEquals(503, (error as AppError.Http).code)
    }

    @Test
    fun `an AppError passes through unchanged`() {
        val original = AppError.NoMatchingRelease()

        assertTrue(original.toAppError() === original)
    }

    /** Mapping cancellation would silently keep a cancelled coroutine running. */
    @Test
    fun `cancellation is rethrown, never mapped`() {
        assertThrows(CancellationException::class.java) {
            CancellationException("stopped").toAppError()
        }
    }

    private fun httpException(code: Int, headers: Map<String, String> = emptyMap()): HttpException {
        val request = Request.Builder().url("https://api.github.com/test").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val body = """{"message":"test"}""".toResponseBody("application/json".toMediaType())
        return HttpException(retrofit2.Response.error<Any>(body, response))
    }
}
