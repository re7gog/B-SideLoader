package dev.re7gog.b_sideloader.data.device

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How much of this app the user can see. */
enum class Visibility {
    /** No activity of this app is started: the user is somewhere else. */
    Hidden,

    /** An activity of this app is on screen, but something — a system dialog, say — is in front. */
    Visible,

    /** An activity of this app is in front, taking input. */
    InFront,
}

/**
 * Whether the user can see this app right now, read from its own activities' lifecycles.
 *
 * The standard installer's confirmation is an activity this app starts, and Android 10+ quietly
 * drops an activity start from an app the user cannot see — no exception, no verdict, nothing. So
 * whoever shows one has to know whether it can be seen; see `SessionApkInstaller`.
 *
 * It registers itself on construction and only sees activities started after that, so the
 * application creates it in `onCreate`, before any activity exists. `androidx.startup` is removed
 * from the manifest, which rules out `ProcessLifecycleOwner`.
 *
 * Stops caused by a configuration change are not counted, so rotating the screen never reads as
 * the user leaving and coming back.
 */
@Singleton
class AppVisibility @Inject constructor(
    @ApplicationContext context: Context,
) : Application.ActivityLifecycleCallbacks {

    private val _state = MutableStateFlow(Visibility.Hidden)
    val state: StateFlow<Visibility> = _state.asStateFlow()

    // Only touched from the main thread, where every lifecycle callback runs.
    private var started = 0
    private var resumed = 0

    /** Activities stopped for a configuration change, whose recreated instance is yet to start. */
    private var restarting = 0

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (restarting > 0) restarting-- else started++
        publish()
    }

    override fun onActivityResumed(activity: Activity) {
        resumed++
        publish()
    }

    override fun onActivityPaused(activity: Activity) {
        resumed = (resumed - 1).coerceAtLeast(0)
        publish()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) restarting++ else started = (started - 1).coerceAtLeast(0)
        publish()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun publish() {
        _state.value = when {
            resumed > 0 -> Visibility.InFront
            started > 0 || restarting > 0 -> Visibility.Visible
            else -> Visibility.Hidden
        }
    }
}
