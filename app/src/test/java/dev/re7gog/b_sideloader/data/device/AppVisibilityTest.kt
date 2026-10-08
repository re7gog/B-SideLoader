package dev.re7gog.b_sideloader.data.device

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** What the installer reads to know whether its confirmation dialog can be seen. */
@RunWith(AndroidJUnit4::class)
class AppVisibilityTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val visibility = AppVisibility(application)
    private val screen = Robolectric.buildActivity(Activity::class.java)

    @Test
    fun hiddenUntilAnActivityStarts() {
        assertEquals(Visibility.Hidden, visibility.state.value)

        screen.create().start()
        assertEquals(Visibility.Visible, visibility.state.value)

        screen.resume()
        assertEquals(Visibility.InFront, visibility.state.value)
    }

    /** A dialog-like activity over this one pauses it without hiding it. */
    @Test
    fun pausedIsVisibleAndStoppedIsHidden() {
        screen.setup()

        screen.pause()
        assertEquals(Visibility.Visible, visibility.state.value)

        screen.stop()
        assertEquals(Visibility.Hidden, visibility.state.value)
    }

    /**
     * Rotating the screen stops and recreates the activity. Read as "the user left and came back",
     * it would show a pending confirmation again on every rotation.
     */
    @Test
    fun aConfigurationChangeNeverReadsAsHidden() {
        screen.setup()
        val seen = mutableListOf<Visibility>()
        application.registerActivityLifecycleCallbacks(Recorder { seen += visibility.state.value })

        screen.recreate()

        assertFalse(Visibility.Hidden in seen)
        assertEquals(Visibility.InFront, visibility.state.value)
    }

    /** Runs [record] after every callback; registered after the tracker, so it sees its state. */
    private class Recorder(private val record: () -> Unit) : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = record()
        override fun onActivityStarted(activity: Activity) = record()
        override fun onActivityResumed(activity: Activity) = record()
        override fun onActivityPaused(activity: Activity) = record()
        override fun onActivityStopped(activity: Activity) = record()
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = record()
        override fun onActivityDestroyed(activity: Activity) = record()
    }
}
