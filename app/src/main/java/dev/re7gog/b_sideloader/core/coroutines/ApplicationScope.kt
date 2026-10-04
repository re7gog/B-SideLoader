package dev.re7gog.b_sideloader.core.coroutines

import javax.inject.Qualifier

/**
 * The application-wide [kotlinx.coroutines.CoroutineScope] that outlives any screen.
 *
 * Lives in `core` rather than next to its provider in `data/di` because the domain needs it too:
 * an install started from one screen must keep running — and stay visible — after that screen is
 * gone.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
