package dev.re7gog.b_sideloader.data.di

import javax.inject.Qualifier

/**
 * OkHttp application interceptors: run once per call, see the request the app made and the final
 * response, and do *not* see redirects or retries. Auth and default headers belong here.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationInterceptors

/**
 * OkHttp network interceptors: run once per network hop, see redirects, and can inspect the
 * bytes actually on the wire. Debug logging belongs here so a redirected release-asset download
 * is logged as the two requests it really is.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkInterceptors

/** The application-wide [kotlinx.coroutines.CoroutineScope] that outlives any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
