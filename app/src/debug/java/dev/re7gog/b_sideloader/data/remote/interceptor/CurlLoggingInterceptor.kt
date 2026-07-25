package dev.re7gog.b_sideloader.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * Debug-only. Logs every outgoing request as a `curl` command you can paste into a terminal to
 * reproduce it exactly — the fastest way to tell "the app built a bad request" apart from "GitHub
 * answered badly", which a plain header dump cannot do.
 *
 * Lives in `src/debug`, so neither this class nor anything it logs exists in a release build.
 *
 * Secrets are replaced with a placeholder rather than printed: a token pasted from a bug report is
 * a token that has to be revoked.
 */
class CurlLoggingInterceptor(
    private val log: (String) -> Unit = { Log.d(TAG, it) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val curl = StringBuilder("curl -v -X ${request.method}")

        request.headers.forEach { (name, value) ->
            val shown = if (name.lowercase() in REDACTED_HEADERS) REDACTED else value
            curl.append(" -H ").append("'$name: $shown'".sanitized())
        }

        request.body?.let { body ->
            // Only text bodies are worth printing, and this app never uploads a binary one.
            val buffer = Buffer().also { body.writeTo(it) }
            if (buffer.size <= MAX_BODY_BYTES) {
                curl.append(" --data ").append("'${buffer.readUtf8()}'".sanitized())
            } else {
                curl.append(" --data '<${buffer.size} bytes omitted>'")
            }
        }

        curl.append(" '").append(request.url).append('\'')
        log(curl.toString())
        return chain.proceed(request)
    }

    /** Keeps a header value with a quote in it from producing an unpastable command. */
    private fun String.sanitized(): String = replace("\n", "").replace("\r", "")

    private companion object {
        const val TAG = "BSide/Curl"
        const val REDACTED = "<redacted>"
        const val MAX_BODY_BYTES = 8L * 1024
        val REDACTED_HEADERS = setOf("authorization", "cookie", "set-cookie", "proxy-authorization")
    }
}
