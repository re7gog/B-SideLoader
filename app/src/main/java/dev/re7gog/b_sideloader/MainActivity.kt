package dev.re7gog.b_sideloader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import dev.re7gog.b_sideloader.ui.BSideLoaderApp
import dev.re7gog.b_sideloader.ui.common.permission.NotificationPermissionGate
import dev.re7gog.b_sideloader.ui.theme.BSideLoaderTheme

/**
 * The single activity.
 *
 * `AppCompatActivity` (rather than `ComponentActivity`) because AppCompat provides the per-app
 * language backport below API 33, which the settings screen relies on.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Once per tap: not again when the activity is recreated, nor when it is reopened from
        // recents with the intent it was first started with.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) {
            intent.getLongArrayExtra(EXTRA_INSTALL_APP_IDS)?.let(viewModel::installWaitingUpdates)
        }

        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val darkTheme = when (theme.mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // The system bar icons are picked from the *system* dark mode, so forcing a light app
            // theme on a dark-mode phone would otherwise leave white icons on a white bar. Re-run
            // it whenever the resolved theme changes rather than only in onCreate.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        darkTheme
                    },
                    navigationBarStyle = SystemBarStyle.auto(
                        NAV_BAR_SCRIM_LIGHT,
                        NAV_BAR_SCRIM_DARK,
                    ) { darkTheme },
                )
                onDispose {}
            }

            BSideLoaderTheme(darkTheme = darkTheme, dynamicColor = theme.dynamicColor) {
                NotificationPermissionGate()
                BSideLoaderApp()
            }
        }
    }

    companion object {
        /**
         * Set by the "updates available" notification: the row ids of the apps whose updates the
         * background sweep left to the user, to install now that they are here.
         */
        const val EXTRA_INSTALL_APP_IDS = "dev.re7gog.b_sideloader.extra.INSTALL_APP_IDS"

        /** The scrims `enableEdgeToEdge` uses by default for a three-button navigation bar. */
        private const val NAV_BAR_SCRIM_LIGHT = 0xE6FFFFFF.toInt()
        private const val NAV_BAR_SCRIM_DARK = 0x801B1B1B.toInt()
    }
}
