package dev.re7gog.b_sideloader.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runs [onResume] every time the screen comes back to the foreground.
 *
 * Needed for state Android does not let an app observe — battery-optimization exemption, the App
 * Standby bucket, OEM autostart — where the moment it can change is precisely the moment the user
 * returns from a system settings activity.
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
