package dev.re7gog.b_sideloader.data.installer

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.re7gog.b_sideloader.data.installer.session.SessionApkInstaller
import dev.re7gog.b_sideloader.data.installer.session.SessionApkInstaller.Companion.RETURN_GRACE
import dev.re7gog.b_sideloader.data.installer.session.SessionApkInstaller.Companion.UNANSWERED_TIMEOUT
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import dev.re7gog.b_sideloader.testing.ShadowCommitOnlySession
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import java.io.ByteArrayInputStream
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The standard install path, through the real `PackageInstaller` session, the real result
 * receivers and the real event bus.
 *
 * Hilt is here only because the receivers are `@AndroidEntryPoint`: the system delivers the
 * verdict to a manifest receiver, which has to reach the same bus the waiting installer listens
 * on. [deliver] plays the system's part, sending the verdict through the `IntentSender` the
 * session was committed with — so it only arrives if the installer tagged that intent correctly.
 *
 * [screen] plays the user's: an activity of this app whose lifecycle says whether they can see the
 * app, which decides whether — and when — the system's confirmation may be shown.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, shadows = [ShadowCommitOnlySession::class])
@RunWith(AndroidJUnit4::class)
class SessionApkInstallerTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var installer: SessionApkInstaller

    @Inject
    lateinit var bus: InstallEventBus

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val packageInstaller get() = application.packageManager.packageInstaller
    private val screen = Robolectric.buildActivity(Activity::class.java)

    @Before
    fun inject() = hilt.inject()

    @Test
    fun aSuccessfulVerdictReportsTheInstalledPackage() = runTest {
        val progress = mutableListOf<Float>()
        val install = startInstall(onProgress = { progress += it })

        deliver(PackageInstaller.STATUS_SUCCESS, packageName = "com.example")

        assertEquals(InstallOutcome.Success("com.example"), install.await())
        assertEquals(1f, progress.last())
    }

    /** The case the bus exists for: another install's verdict must not end this one. */
    @Test
    fun aVerdictForAnotherRequestIsIgnored() = runTest {
        val install = startInstall()

        bus.publish(
            PackageInstallerEvent(
                requestId = bus.newRequestId(), // one nobody else holds
                status = PackageInstaller.STATUS_SUCCESS,
            )
        )
        runCurrent()
        assertFalse(install.isCompleted)

        deliver(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallFailure.Aborted, install.await().failure())
    }

    @Test
    fun aPendingUserActionShowsTheConfirmationAndKeepsWaiting() = runTest {
        screen.setup()
        val install = startInstall()

        askToConfirm()

        val shown = checkNotNull(shadowOf(application).nextStartedActivity)
        assertEquals(CONFIRM_INSTALL, shown.action)
        assertTrue(shown.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(install.isCompleted)

        deliver(PackageInstaller.STATUS_SUCCESS, packageName = "com.example")
        assertEquals(InstallOutcome.Success("com.example"), install.await())
    }

    /**
     * The background sweep: nobody is there to answer, and Android would drop the dialog anyway.
     * Waiting for a verdict that never comes used to hold the install slot for good.
     */
    @Test
    fun withoutTheUserAConfirmationIsNeverShownAndTheSessionIsDropped() = runTest {
        screen.setup() // even with the app open: a background install never asks
        val install = startInstall(interactive = false)

        askToConfirm()

        assertEquals(InstallOutcome.NeedsConfirmation, install.await())
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(packageInstaller.allSessions.isEmpty())
    }

    /** The user tapped Update and left during the download: they get the question on return. */
    @Test
    fun aConfirmationWaitsUntilTheAppCanBeSeen() = runTest {
        val install = startInstall()

        askToConfirm()
        assertNull(shadowOf(application).nextStartedActivity)
        assertFalse(install.isCompleted)

        screen.setup()
        runCurrent()
        assertEquals(CONFIRM_INSTALL, shadowOf(application).nextStartedActivity?.action)

        deliver(PackageInstaller.STATUS_SUCCESS, packageName = "com.example")
        assertEquals(InstallOutcome.Success("com.example"), install.await())
    }

    @Test
    fun aConfirmationNobodyComesBackForIsDropped() = runTest {
        val install = startInstall()

        askToConfirm()
        advanceTimeBy(UNANSWERED_TIMEOUT + 1.seconds)

        assertEquals(InstallOutcome.NeedsConfirmation, install.await())
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(packageInstaller.allSessions.isEmpty())
    }

    /** Home on the dialog: nothing would ever bring it back, so the app asks again on return. */
    @Test
    fun aConfirmationLeftUnansweredIsShownAgainWhenTheUserComesBack() = runTest {
        screen.setup()
        val install = startInstall()
        askToConfirm()
        checkNotNull(shadowOf(application).nextStartedActivity)

        leaveAndComeBack()
        assertNull(shadowOf(application).nextStartedActivity)

        advanceTimeBy(RETURN_GRACE + 1.milliseconds)
        assertEquals(CONFIRM_INSTALL, shadowOf(application).nextStartedActivity?.action)

        deliver(PackageInstaller.STATUS_SUCCESS, packageName = "com.example")
        assertEquals(InstallOutcome.Success("com.example"), install.await())
    }

    /** Answered just before switching back: its verdict is on the way, so no second dialog. */
    @Test
    fun aVerdictArrivingRightAfterTheUserComesBackIsNotAskedAgain() = runTest {
        screen.setup()
        val install = startInstall()
        askToConfirm()
        checkNotNull(shadowOf(application).nextStartedActivity)

        leaveAndComeBack()
        deliver(PackageInstaller.STATUS_SUCCESS, packageName = "com.example")

        assertEquals(InstallOutcome.Success("com.example"), install.await())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun aConfirmationNeverAnsweredIsEventuallyDropped() = runTest {
        screen.setup()
        val install = startInstall()
        askToConfirm()
        screen.pause() // the dialog stays up, and nobody touches it

        advanceTimeBy(UNANSWERED_TIMEOUT + 1.seconds)

        assertEquals(InstallOutcome.NeedsConfirmation, install.await())
        assertTrue(packageInstaller.allSessions.isEmpty())
    }

    /** One status code, several unrelated problems; the framework's message tells them apart. */
    @Test
    fun conflictsAreSplitByTheFrameworksMessage() = runTest {
        val cases = mapOf(
            "INSTALL_FAILED_VERSION_DOWNGRADE" to InstallFailure.Downgrade,
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match" to InstallFailure.SignatureMismatch,
            "INSTALL_FAILED_DUPLICATE_PERMISSION" to InstallFailure.Conflict,
        )
        for ((message, expected) in cases) {
            val install = startInstall()

            deliver(PackageInstaller.STATUS_FAILURE_CONFLICT, message = message)

            assertEquals(message, expected, install.await().failure())
        }
    }

    /** A truncated download must not leave a half-written session holding disk space. */
    @Test
    fun aTruncatedPayloadFailsAndAbandonsTheSession() = runTest {
        val payload = ApkPayload(lengthBytes = 100, stream = ByteArrayInputStream(ByteArray(40)))

        val outcome = installer.install(payload, interactive = true) {}

        assertEquals(InstallFailure.BadPayload, outcome.failure())
        assertTrue(packageInstaller.allSessions.isEmpty())
    }

    /** Leaving the screen while the system dialog is up cancels the install — and its session. */
    @Test
    fun cancellingAbandonsTheSession() = runTest {
        val install = startInstall()
        assertEquals(1, packageInstaller.allSessions.size)

        install.cancel()
        runCurrent()

        assertTrue(packageInstaller.allSessions.isEmpty())
    }

    @Test
    fun uninstallReportsTheSystemsVerdict() = runTest {
        val uninstall = async { installer.uninstall("com.example") }
        runCurrent()

        val statusReceiver = checkNotNull(
            shadowOf(packageInstaller).getLastUninstalledStatusReceiver("com.example")
        )
        statusReceiver.sendIntent(application, 0, verdict(PackageInstaller.STATUS_SUCCESS), null, null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(UninstallOutcome.Success("com.example"), uninstall.await())
    }

    // ---- The system's side ----

    /** Starts an install of a small, complete payload and runs it up to the commit. */
    private fun TestScope.startInstall(
        interactive: Boolean = true,
        onProgress: (Float) -> Unit = {},
    ): Deferred<InstallOutcome> {
        val bytes = ByteArray(PAYLOAD_BYTES) { it.toByte() }
        val install = async {
            installer.install(ApkPayload(bytes.size.toLong(), ByteArrayInputStream(bytes)), interactive) {
                onProgress(it)
            }
        }
        runCurrent()
        return install
    }

    /** The system's answer to a commit it will not take without the user. */
    private fun TestScope.askToConfirm() =
        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION, userAction = Intent(CONFIRM_INSTALL))

    /** The dialog comes up over the app, the user presses Home, then opens the app again. */
    private fun TestScope.leaveAndComeBack() {
        screen.pause()
        screen.stop()
        runCurrent()
        screen.start().resume()
        runCurrent()
    }

    /** What `PackageInstaller` does with a verdict: fill it into the committed status receiver. */
    private fun TestScope.deliver(
        status: Int,
        packageName: String? = null,
        message: String? = null,
        userAction: Intent? = null,
    ) {
        val latest = packageInstaller.allSessions.maxOf { it.sessionId }
        val session = Shadow.extract<ShadowCommitOnlySession>(packageInstaller.openSession(latest))
        val statusReceiver = checkNotNull(session.statusReceiver) {
            "Session $latest was never committed"
        }

        val verdict = verdict(status, packageName, message, userAction)
        statusReceiver.sendIntent(application, 0, verdict, null, null)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun verdict(
        status: Int,
        packageName: String? = null,
        message: String? = null,
        userAction: Intent? = null,
    ) = Intent().apply {
        putExtra(PackageInstaller.EXTRA_STATUS, status)
        packageName?.let { putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, it) }
        message?.let { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, it) }
        userAction?.let { putExtra(Intent.EXTRA_INTENT, it) }
    }

    private fun InstallOutcome.failure(): InstallFailure? =
        ((this as? InstallOutcome.Failure)?.error as? AppError.Install)?.reason

    private companion object {
        const val PAYLOAD_BYTES = 256 * 1024
        const val CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
