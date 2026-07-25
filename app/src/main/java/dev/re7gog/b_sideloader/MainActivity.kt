package dev.re7gog.b_sideloader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
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

        if (intent?.getBooleanExtra(EXTRA_RUN_UPDATE_CHECK, false) == true) {
            viewModel.runUpdateCheckNow()
        }

        setContent {
            val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
            BSideLoaderTheme(dynamicColor = useDynamicColor) {
                NotificationPermissionGate()
                BSideLoaderApp()
            }
        }
    }

    companion object {
        /** Set by the "updates available" notification so opening it starts a check. */
        const val EXTRA_RUN_UPDATE_CHECK = "dev.re7gog.b_sideloader.extra.RUN_UPDATE_CHECK"
    }
}
