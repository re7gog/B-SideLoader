package dev.re7gog.b_sideloader.domain.installer

import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallSchedulerTest {

    private val settings = FakeSettingsRepository()
    private val scheduler = InstallScheduler(settings)

    @Test
    fun `one download per source by default, sources independent`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        listOf(
            "github 1" to AppSourceKind.GitHub,
            "github 2" to AppSourceKind.GitHub,
            "telegram 1" to AppSourceKind.Telegram,
        ).forEach { (name, source) ->
            backgroundScope.launch { scheduler.download(source) { started += name; gate.await() } }
        }
        runCurrent()

        assertEquals(listOf("github 1", "telegram 1"), started)

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("github 1", "telegram 1", "github 2"), started)
    }

    @Test
    fun `parallel updates allow three downloads per source`() = runTest {
        settings.setParallelUpdates(true)
        val started = mutableListOf<Int>()
        repeat(4) { n ->
            backgroundScope.launch {
                scheduler.download(AppSourceKind.GitHub) { started += n; CompletableDeferred<Unit>().await() }
            }
        }
        runCurrent()

        assertEquals(AppSettings.MAX_PARALLEL_DOWNLOADS_PER_SOURCE, started.size)
    }

    /** Read live: turning the setting on starts the downloads that were waiting for a slot. */
    @Test
    fun `turning parallel updates on lets waiting downloads start`() = runTest {
        val started = mutableListOf<Int>()
        repeat(3) { n ->
            backgroundScope.launch {
                scheduler.download(AppSourceKind.Telegram) { started += n; CompletableDeferred<Unit>().await() }
            }
        }
        runCurrent()
        assertEquals(1, started.size)

        settings.setParallelUpdates(true)
        runCurrent()

        assertEquals(3, started.size)
    }

    @Test
    fun `installs run one at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        repeat(2) { n -> backgroundScope.launch { scheduler.install { started += n; gate.await() } } }
        runCurrent()

        assertEquals(listOf(0), started)

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(0, 1), started)
    }

    /**
     * B-SideLoader's own update kills the process, so it must not install while another install is
     * still downloading — even though the installer itself is free.
     */
    @Test
    fun `a last install waits for every other install in flight`() = runTest {
        val download = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        backgroundScope.launch {
            scheduler.track {
                scheduler.download(AppSourceKind.GitHub) { download.await() }
                scheduler.install { events += "other installed" }
            }
        }
        backgroundScope.launch {
            scheduler.track { scheduler.install(last = true) { events += "self installed" } }
        }
        runCurrent()

        assertEquals(emptyList<String>(), events)

        download.complete(Unit)
        runCurrent()

        assertEquals(listOf("other installed", "self installed"), events)
    }
}
