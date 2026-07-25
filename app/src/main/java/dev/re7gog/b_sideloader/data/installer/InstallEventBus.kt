package dev.re7gog.b_sideloader.data.installer

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One `PackageInstaller` result, tagged with the request that produced it.
 *
 * [requestId] is what makes the result attributable. The old code broadcast install results on a
 * global object with no id, so every open details screen received every result and had to guess —
 * with a `installRequested` boolean — whether it was theirs. Two installs in flight, or an install
 * started from the update worker while a details screen was open, and the wrong app got saved.
 */
data class PackageInstallerEvent(
    val requestId: Int,
    val status: Int,
    val message: String? = null,
    val packageName: String? = null,
    /** Present only for `STATUS_PENDING_USER_ACTION`: the confirmation UI to launch. */
    val userAction: Intent? = null,
)

/**
 * Carries results from the manifest-declared broadcast receivers back to the coroutine that
 * started the install.
 *
 * Callers must subscribe *before* committing the session — see `awaitResult` in
 * [session.SessionApkInstaller] — because there is no replay: a result that arrives with nobody
 * listening is genuinely lost, and buffering it would only make a later, unrelated install
 * consume a stale event.
 */
@Singleton
class InstallEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<PackageInstallerEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
    )
    val events: SharedFlow<PackageInstallerEvent> = _events.asSharedFlow()

    private val nextRequestId = AtomicInteger(1)

    /** A request id that is unique for the lifetime of the process. */
    fun newRequestId(): Int = nextRequestId.getAndIncrement()

    fun publish(event: PackageInstallerEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
