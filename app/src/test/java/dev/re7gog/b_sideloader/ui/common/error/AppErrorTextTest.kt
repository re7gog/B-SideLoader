package dev.re7gog.b_sideloader.ui.common.error

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.domain.error.AiFailure
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.InstallFailure
import dev.re7gog.b_sideloader.domain.error.PrivilegedFailure
import dev.re7gog.b_sideloader.ui.common.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Every failure the app can report, rendered in every language it ships.
 *
 * `toUiText` compiles as long as each case names *some* resource; what only rendering proves is
 * that the arguments fit that resource's placeholders in each translation — a `%1$d` fed a string,
 * or a translation that dropped the `%1$s` carrying the detail, fails here instead of in front of
 * a user who hit the error.
 */
@RunWith(AndroidJUnit4::class)
class AppErrorTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locales = listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))

    @Test
    fun everyFailureRendersInEveryLanguage() {
        for (locale in locales) {
            for (error in allErrors()) {
                val text = error.toUiText().resolve(context.inLocale(locale))
                assertTrue("${error::class.simpleName} is blank in $locale", text.isNotBlank())
            }
        }
    }

    /** The detail is the only thing telling the user *what* went wrong; no translation may drop it. */
    @Test
    fun detailsSurviveEveryTranslation() {
        val cases = mapOf(
            AppError.Http(503) to "503",
            AppError.Telegram(400, "CHAT_INVALID") to "CHAT_INVALID",
            AppError.Install(InstallFailure.Conflict, "INSTALL_FAILED_DUPLICATE_PERMISSION") to
                "INSTALL_FAILED_DUPLICATE_PERMISSION",
            AppError.Install(InstallFailure.Rejected, "policy") to "policy",
            AppError.Ai(AiFailure.Service, "model not found") to "model not found",
            AppError.Ai(AiFailure.QuotaExceeded, "insufficient_quota") to "insufficient_quota",
        )
        for (locale in locales) {
            for ((error, detail) in cases) {
                val text = error.toUiText().resolve(context.inLocale(locale))
                assertTrue("$locale: \"$text\" lost \"$detail\"", text.contains(detail))
            }
        }
    }

    @Test
    fun aRateLimitWithAResetTimeSaysWhenItEnds() {
        val withReset = AppError.RateLimited(resetAtEpochSeconds = 1_700_000_000L).toUiText()
        val withoutReset = AppError.RateLimited().toUiText()

        assertEquals(R.string.error_rate_limited_until, (withReset as UiText.Res).id)
        assertEquals(R.string.error_rate_limited, (withoutReset as UiText.Res).id)
    }

    @Test
    fun anUnexpectedErrorShowsItsCausesMessage() {
        val text = AppError.Unexpected(IllegalStateException("database is locked")).toUiText()

        assertEquals(UiText.Raw("database is locked"), text)
    }

    /**
     * Without a message from the cause there is nothing specific to say, so the generic text is
     * shown — in the user's language, not the English placeholder `AppError` uses for logs.
     */
    @Test
    fun anUnexpectedErrorWithoutAMessageIsLocalised() {
        val ru = context.inLocale(Locale.forLanguageTag("ru"))

        val text = AppError.Unexpected(IllegalStateException()).toUiText().resolve(ru)

        assertEquals(ru.getString(R.string.error_unexpected), text)
        assertNotEquals(context.getString(R.string.error_unexpected), text)
    }

    /** Anything that is not an `AppError` still gets the generic text rather than a raw message. */
    @Test
    fun aThrowableThatIsNotAnAppErrorGetsTheGenericText() {
        assertEquals(UiText.of(R.string.error_unexpected), RuntimeException("NPE at line 3").toUiText())
    }

    private fun allErrors(): List<AppError> = buildList {
        add(AppError.Network())
        add(AppError.Http(404))
        add(AppError.Unauthorized())
        add(AppError.RateLimited())
        add(AppError.RateLimited(resetAtEpochSeconds = 1_700_000_000L))
        add(AppError.NotFound())
        add(AppError.Telegram(400, "CHAT_INVALID"))
        add(AppError.TelegramNotAuthorized())
        add(AppError.NoMatchingRelease())
        add(AppError.Storage("The selected file could not be opened"))
        InstallFailure.entries.forEach {
            add(AppError.Install(it))
            add(AppError.Install(it, detail = "INSTALL_FAILED_SOMETHING"))
        }
        PrivilegedFailure.entries.forEach { add(AppError.Privileged(it)) }
        AiFailure.entries.forEach {
            add(AppError.Ai(it))
            add(AppError.Ai(it, detail = "model not found"))
        }
        add(AppError.Unexpected(IllegalStateException("boom")))
        add(AppError.Unexpected(null))
    }

    private fun UiText.resolve(context: Context): String = when (this) {
        is UiText.Raw -> value
        is UiText.Res -> context.getString(id, *args.toTypedArray())
    }

    private fun Context.inLocale(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })
}
