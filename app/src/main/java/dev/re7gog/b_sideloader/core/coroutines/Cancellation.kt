package dev.re7gog.b_sideloader.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Cancellation-safe error handling.
 *
 * Structured concurrency cancels a coroutine by throwing [CancellationException] inside it. A
 * `catch (e: Exception)` or a bare `runCatching { }` swallows that exception, which leaves the
 * coroutine running after its scope was cancelled — the job never completes, `join()` hangs, and
 * work keeps firing after the ViewModel or screen is gone.
 *
 * Every catch-all in this codebase therefore goes through one of the helpers below.
 */

/** Rethrows `this` when it is the signal that the current coroutine was cancelled. */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * [runCatching] that lets cancellation through.
 *
 * Use for non-suspending blocks. For suspending blocks use [suspendRunCatching], which is the
 * only variant that can guarantee the block itself is inlined into a suspend context.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    // AT_MOST_ONCE, not EXACTLY_ONCE: the block is invoked inside a `try`, so from the compiler's
    // point of view it may not complete.
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/** Suspending [runCatchingCancellable]. */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> {
    // AT_MOST_ONCE, not EXACTLY_ONCE: the block is invoked inside a `try`, so from the compiler's
    // point of view it may not complete.
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
