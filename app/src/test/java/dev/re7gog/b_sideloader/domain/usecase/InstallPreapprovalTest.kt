package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.installer.InstallScheduler
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.PreapprovalSession
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.testing.FakeAppsRepository
import dev.re7gog.b_sideloader.testing.FakeInstallerGateway
import dev.re7gog.b_sideloader.testing.FakeSelfAppInfo
import dev.re7gog.b_sideloader.testing.FakeSelfUpdateStateRepository
import dev.re7gog.b_sideloader.testing.FakeSettingsRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.reconcileSelfUpdate
import dev.re7gog.b_sideloader.testing.selfApp
import dev.re7gog.b_sideloader.testing.updateCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asking the user to approve an install up front — Android 14's pre-approval — as the domain sees
 * it: in-app installs only, alongside the download, quiet on decline, the usual path otherwise.
 * Where asking makes sense at all is the gateway's call (see `PreapprovalPolicyTest`); here the
 * fake gateway opens a session only when [FakeInstallerGateway.preapproval] is set.
 */
class InstallPreapprovalTest {

    private val installer = FakeInstallerGateway()
    private val apps = FakeAppsRepository(
        listOf(
            githubApp(id = 1L, name = "Alpha", packageName = "com.alpha", version = AppVersion("v1.0")),
            githubApp(id = 2L, name = "Beta", packageName = "com.beta", version = AppVersion("v1.0")),
        ),
    )
    private val selfUpdates = FakeSelfUpdateStateRepository()
    private val selfInfo = FakeSelfAppInfo()
    private val candidate = updateCandidate("v2.0")

    private val installApp = InstallAppUseCase(
        installer,
        InstallScheduler(FakeSettingsRepository()),
        apps,
        FakeTelegramRepository(),
        selfUpdates,
        reconcileSelfUpdate(apps, selfUpdates, selfInfo),
        selfInfo,
        NoopLogger,
    )

    private fun TestScope.coordinator() = InstallCoordinator(installApp, backgroundScope, NoopLogger)

    private suspend fun app(id: Long): TrackedApp = apps.getApp(id)!!

    @Test
    fun `an approved install goes into the approved session`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val coordinator = coordinator()

        coordinator.install(app(1L), candidate)
        runCurrent()

        val session = installer.openedSessions.single()
        assertEquals("com.alpha", session.packageName)
        assertEquals(listOf(session), installer.askedSessions)
        assertEquals(listOf<PreapprovalSession?>(session), installer.committedSessions)
        assertTrue(installer.abandonedSessions.isEmpty())
        assertEquals("v2.0", app(1L).version.raw)
    }

    /**
     * Declining while the APK downloads stops the download there and then, and ends the install
     * with nothing to report — no failure, no message, the row just goes back to how it was.
     */
    @Test
    fun `declining cancels the download quietly`() = runTest {
        installer.preapproval = PreapprovalDecision.Declined
        val downloadCancelled = CompletableDeferred<Unit>()
        installer.duringDownload = {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                downloadCancelled.complete(Unit)
            }
        }
        val coordinator = coordinator()
        val results = mutableListOf<InstallResult>()
        backgroundScope.launch { coordinator.results.collect { results += it } }
        runCurrent()

        coordinator.install(app(1L), candidate)
        runCurrent()

        assertTrue(downloadCancelled.isCompleted)
        assertTrue(installer.committed.isEmpty())
        assertTrue(results.isEmpty())
        assertTrue(coordinator.installs.value.isEmpty())
        assertEquals("v1.0", app(1L).version.raw)
    }

    /** Blocked on the device, or failed: the session is dropped and the install goes the usual way. */
    @Test
    fun `when asking is unavailable the install goes the usual way`() = runTest {
        installer.preapproval = PreapprovalDecision.Unavailable
        val coordinator = coordinator()

        coordinator.install(app(1L), candidate)
        runCurrent()

        assertEquals(installer.openedSessions, installer.abandonedSessions)
        assertEquals(listOf<PreapprovalSession?>(null), installer.committedSessions)
        assertEquals("v2.0", app(1L).version.raw)
    }

    /** The background sweep has no screen to show a dialog on, so it never asks. */
    @Test
    fun `background installs never ask`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val coordinator = coordinator()

        coordinator.installAndAwait(app(1L), candidate)

        assertTrue(installer.openedSessions.isEmpty())
        assertEquals(listOf<PreapprovalSession?>(null), installer.committedSessions)
    }

    /** An app opened from search has no package name yet — nothing to ask about. */
    @Test
    fun `an app with no package yet is never asked about`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val coordinator = coordinator()

        coordinator.install(githubApp(id = TrackedApp.NEW_APP_ID, packageName = ""), candidate)
        runCurrent()

        assertTrue(installer.openedSessions.isEmpty())
        assertEquals(1, installer.committed.size)
    }

    /** "Update all" asks for each app — one dialog after another, never piled up. */
    @Test
    fun `one approval dialog at a time`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val firstAnswer = CompletableDeferred<Unit>()
        installer.whileAsking = { session -> if (session.packageName == "com.alpha") firstAnswer.await() }
        val coordinator = coordinator()

        coordinator.install(app(1L), candidate)
        coordinator.install(app(2L), candidate)
        runCurrent()

        assertEquals(listOf("com.alpha"), installer.askedSessions.map { it.packageName })

        firstAnswer.complete(Unit)
        runCurrent()

        assertEquals(listOf("com.alpha", "com.beta"), installer.askedSessions.map { it.packageName })
        assertEquals(2, installer.committed.size)
    }

    /**
     * The download does not wait for the answer, but the install does: committing while the
     * dialog is still up would put a second dialog over it.
     */
    @Test
    fun `the install waits for the answer, the download does not`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val answer = CompletableDeferred<Unit>()
        installer.whileAsking = { answer.await() }
        val coordinator = coordinator()

        coordinator.install(app(1L), candidate)
        runCurrent()

        assertEquals(1, installer.installed.size)
        assertTrue(installer.committed.isEmpty())

        answer.complete(Unit)
        runCurrent()

        assertEquals(1, installer.committed.size)
    }

    /** A declined self-update did not happen, so nothing is left for the next process to judge. */
    @Test
    fun `a declined self-update leaves no pending record`() = runTest {
        installer.preapproval = PreapprovalDecision.Declined
        val self = selfApp(id = 9L, version = AppVersion("v1.0"))
        apps.add(self)

        val events = installApp(self, candidate, interactive = true).toList()

        assertEquals(AppInstallEvent.Declined, events.last())
        assertNull(selfUpdates.pending)
    }

    /** A recorded self-update is the precondition the test above relies on. */
    @Test
    fun `an approved self-update is recorded as pending`() = runTest {
        installer.preapproval = PreapprovalDecision.Approved
        val self = selfApp(id = 9L, version = AppVersion("v1.0"))
        apps.add(self)

        installApp(self, candidate, interactive = true).toList()

        assertEquals(PendingSelfUpdate(appId = 9L, releaseName = AppVersion("v2.0")), selfUpdates.pending)
    }
}
