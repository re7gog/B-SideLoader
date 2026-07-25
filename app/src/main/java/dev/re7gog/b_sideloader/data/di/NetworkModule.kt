package dev.re7gog.b_sideloader.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dev.re7gog.b_sideloader.data.remote.api.GithubApi
import dev.re7gog.b_sideloader.data.remote.interceptor.GithubAuthInterceptor
import dev.re7gog.b_sideloader.data.remote.interceptor.GithubHeadersInterceptor
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Declares the interceptor sets and binds the ones that ship in every build.
 *
 * Debug-only interceptors are contributed from `src/debug` (see `DebugNetworkModule`), so nothing
 * that logs request or response content is even compiled into a release APK. The [Multibinds]
 * declarations are what make that possible: without them Dagger would fail to build a release
 * variant where the set happens to be empty.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkInterceptorModule {

    @Multibinds
    @ApplicationInterceptors
    abstract fun applicationInterceptors(): Set<Interceptor>

    @Multibinds
    @NetworkInterceptors
    abstract fun networkInterceptors(): Set<Interceptor>

    @Binds
    @IntoSet
    @ApplicationInterceptors
    abstract fun bindGithubHeaders(impl: GithubHeadersInterceptor): Interceptor

    @Binds
    @IntoSet
    @ApplicationInterceptors
    abstract fun bindGithubAuth(impl: GithubAuthInterceptor): Interceptor
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // GitHub sends `null` for optional strings rather than omitting them.
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationInterceptors applicationInterceptors: Set<@JvmSuppressWildcards Interceptor>,
        @NetworkInterceptors networkInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = OkHttpClient.Builder()
        .apply {
            applicationInterceptors.forEach(::addInterceptor)
            networkInterceptors.forEach(::addNetworkInterceptor)
        }
        .followRedirects(true) // release assets redirect to objects.githubusercontent.com
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Read timeout is the gap *between* reads, not the total transfer time, so a normal value
        // is safe even for a 100 MB APK on a slow link — and unlike the 30-minute value it
        // replaces, it actually surfaces a stalled connection instead of hanging half an hour.
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideGithubApi(okHttpClient: OkHttpClient, json: Json): GithubApi = Retrofit.Builder()
        .baseUrl(GithubApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()
        .create(GithubApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val JSON_MEDIA_TYPE = "application/json"
}
