package dev.re7gog.b_sideloader

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps [BSideApplication] for Hilt's test application during instrumented tests.
 *
 * Required because the real `Application` starts TDLib and reconciles background work on create —
 * neither of which a test wants, and both of which would fail on a device with no Telegram
 * session. Registered as `testInstrumentationRunner` in `app/build.gradle.kts`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
