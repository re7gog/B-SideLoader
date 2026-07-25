package dev.re7gog.b_sideloader.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable indirection over [Dispatchers]. Everything that switches threads takes one of these
 * instead of touching [Dispatchers] directly, so tests can substitute a single
 * `StandardTestDispatcher` and drive the whole graph deterministically.
 */
interface DispatcherProvider {
    /** UI thread. Only for code that must touch views/`Drawable`s. */
    val main: CoroutineDispatcher

    /** Blocking I/O: network, disk, `PackageManager`, binder round trips. */
    val io: CoroutineDispatcher

    /** CPU-bound work: filtering, parsing, mapping. */
    val default: CoroutineDispatcher
}

@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
