package dev.atomix.launcher.atomix

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.ATOMIX_ACTIVITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AtomixIntentsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun opensAtomixActivity_inItsOwnTask() {
        val i = atomixIntent(ctx)
        assertEquals(ATOMIX_ACTIVITY, i.component!!.className)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(i.hasExtra(EXTRA_ROUTE))
    }

    @Test fun liveSettingsRoute_isPassed() {
        assertEquals("#/settings/live", atomixIntent(ctx, ROUTE_LIVE_SETTINGS).getStringExtra(EXTRA_ROUTE))
    }

    @Test fun nonHashRoute_isDropped() {
        assertFalse(atomixIntent(ctx, "https://x").hasExtra(EXTRA_ROUTE))
    }
}
