package dev.re7gog.b_sideloader.testing

import android.content.IntentSender
import android.content.pm.PackageInstaller
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller

/**
 * A `PackageInstaller.Session` that only records its commit.
 *
 * Robolectric's own session reports success the moment it is committed, through an intent with no
 * `EXTRA_STATUS` at all — which no real `PackageInstaller` sends, and which the result receiver
 * rightly reads as a failure. With this shadow nothing happens on commit, and a test plays the
 * system's part by sending a verdict through [statusReceiver], the `IntentSender` the installer
 * handed over.
 *
 * Use with `@Config(shadows = [ShadowCommitOnlySession::class])`.
 */
@Implements(PackageInstaller.Session::class)
class ShadowCommitOnlySession : ShadowPackageInstaller.ShadowSession() {

    var statusReceiver: IntentSender? = null
        private set

    @Implementation
    public override fun commit(statusReceiver: IntentSender) {
        this.statusReceiver = statusReceiver
    }
}
