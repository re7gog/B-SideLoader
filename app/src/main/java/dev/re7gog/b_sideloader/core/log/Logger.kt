package dev.re7gog.b_sideloader.core.log

import android.util.Log
import dev.re7gog.b_sideloader.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin logging seam.
 *
 * Exists so that (a) unit tests can assert on or silence logging without Robolectric shadowing
 * `android.util.Log`, and (b) debug-only chatter is compiled behind a lambda, so release builds
 * never even build the message string.
 */
interface Logger {
    fun d(tag: String, message: () -> String)
    fun i(tag: String, message: () -> String)
    fun w(tag: String, throwable: Throwable? = null, message: () -> String)
    fun e(tag: String, throwable: Throwable? = null, message: () -> String)
}

@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag(tag), message())
    }

    override fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag(tag), message())
    }

    override fun w(tag: String, throwable: Throwable?, message: () -> String) {
        Log.w(tag(tag), message(), throwable)
    }

    override fun e(tag: String, throwable: Throwable?, message: () -> String) {
        Log.e(tag(tag), message(), throwable)
    }

    /** Logcat truncates tags over 23 chars on older APIs; prefix keeps app logs greppable. */
    private fun tag(tag: String): String = "BSide/$tag".take(MAX_TAG_LENGTH)

    private companion object {
        const val MAX_TAG_LENGTH = 23
    }
}

/** Discards everything. Default in tests that do not care about logging. */
object NoopLogger : Logger {
    override fun d(tag: String, message: () -> String) = Unit
    override fun i(tag: String, message: () -> String) = Unit
    override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit
    override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit
}
