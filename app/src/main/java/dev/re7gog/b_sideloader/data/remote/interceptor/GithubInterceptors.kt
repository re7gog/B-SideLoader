package dev.re7gog.b_sideloader.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current GitHub token without suspending.
 *
 * OkHttp interceptors run on a blocking dispatcher thread, so they cannot call a `suspend`
 * repository. Rather than `runBlocking` inside the network stack, the secret store — which is
 * SharedPreferences plus an in-memory cache — exposes this synchronous view.
 */
fun interface AuthTokenSource {
    /** The token, or `null` when the user has not configured one. */
    fun currentToken(): String?
}

/**
 * Adds the headers GitHub's REST API expects to every request.
 *
 * `X-GitHub-Api-Version` pins the API contract: without it GitHub is free to serve a newer,
 * differently-shaped response and deserialization starts failing on a day nobody touched the app.
 */
@Singleton
class GithubHeadersInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", ACCEPT)
            .header("User-Agent", USER_AGENT)
            .header("X-GitHub-Api-Version", API_VERSION)
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val USER_AGENT = "B-SideLoader"
        const val ACCEPT = "application/vnd.github+json"

        /** Bump deliberately, together with a check of the changelog. */
        const val API_VERSION = "2022-11-28"
    }
}

/**
 * Attaches the user's personal access token, when there is one.
 *
 * Anonymous GitHub API access is capped at 60 requests/hour, which a user tracking a dozen repos
 * exhausts in a single sweep; an authenticated one gets 5000. The token is read per request so
 * that saving a new token in settings takes effect immediately, with no client rebuild.
 *
 * The previous code passed the raw token as the `Authorization` value. GitHub requires a scheme,
 * so those requests were silently treated as anonymous.
 */
@Singleton
class GithubAuthInterceptor @Inject constructor(
    private val tokenSource: AuthTokenSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenSource.currentToken()?.takeIf { it.isNotBlank() }
        // Only decorate GitHub's own API. Release assets are served from a different host that
        // rejects (or, worse, logs) a bearer token meant for api.github.com.
        if (token == null || request.url.host !in AUTHORIZED_HOSTS) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }

    private companion object {
        val AUTHORIZED_HOSTS = setOf("api.github.com")
    }
}
