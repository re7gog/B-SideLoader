package dev.re7gog.b_sideloader.data.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.re7gog.b_sideloader.data.remote.interceptor.CurlLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

/**
 * Debug-only network instrumentation.
 *
 * This file (and everything it references) lives in `src/debug`, so a release build contains no
 * HTTP logging at all — not disabled at runtime, absent. The previous setup added
 * `HttpLoggingInterceptor` at `Level.BODY` unconditionally, which in release meant the user's
 * GitHub token was written to logcat on every request *and* every APK download was buffered
 * entirely into memory so its bytes could be "logged".
 *
 * Two interceptors, at the two levels that answer different questions:
 *  - a [CurlLoggingInterceptor] *application* interceptor, so you see the request the app built;
 *  - an [HttpLoggingInterceptor] *network* interceptor, so you see each hop including redirects.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @Singleton
    @ApplicationInterceptors
    @IntoSet
    fun provideCurlLoggingInterceptor(): Interceptor = CurlLoggingInterceptor()

    @Provides
    @Singleton
    @NetworkInterceptors
    @IntoSet
    fun provideHttpLoggingInterceptor(): Interceptor = BodyAwareLoggingInterceptor()
}

/**
 * Logs full bodies for the JSON API and headers only for everything else.
 *
 * [HttpLoggingInterceptor] at `Level.BODY` buffers the entire response into memory before printing
 * it. That is exactly what you want for a 3 KB release listing and exactly what you must never do
 * for a 150 MB APK, so the level is chosen per request instead of globally.
 */
private class BodyAwareLoggingInterceptor : Interceptor {

    private val verbose = HttpLoggingInterceptor(::logLine).apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactSecrets()
    }

    private val terse = HttpLoggingInterceptor(::logLine).apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactSecrets()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val isJsonApi = chain.request().url.host in JSON_API_HOSTS
        return if (isJsonApi) verbose.intercept(chain) else terse.intercept(chain)
    }

    private fun HttpLoggingInterceptor.redactSecrets() {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }

    private fun logLine(message: String) {
        Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "BSide/Http"
        val JSON_API_HOSTS = setOf("api.github.com")
    }
}
