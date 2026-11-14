package dev.atomix.launcher.apps

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TvSettingsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** Makes [action] something the (fake) TV can open. */
    private fun tvOpens(action: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "tv.settings"; name = "Page" } }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(action), info)
    }

    // An intent nothing handles throws, as it does on a real TV.
    @Before fun strict() = shadowOf(app).checkActivities(true)

    @Test fun opensTheTvsMainSettings() {
        tvOpens(Settings.ACTION_SETTINGS)
        assertTrue(app.openTvSettings())
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    /** On this TV both accessibility links start without error and show nothing, so they must not be tried first. */
    @Test fun doesNotUseTheAccessibilityLinks_whichOpenNothingOnThisTv() {
        tvOpens(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        tvOpens("android.settings.ACCESSIBILITY_TV_OEM_LINK")
        tvOpens(Settings.ACTION_SETTINGS)
        assertTrue(app.openTvSettings())
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test fun opensInAFreshTask_soBackComesStraightBack() {
        tvOpens(Settings.ACTION_SETTINGS)
        app.openTvSettings()
        val flags = shadowOf(app).nextStartedActivity.flags
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    @Test fun saysSo_whenItCannotOpen() = assertFalse(app.openTvSettings())
}
