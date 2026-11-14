package dev.atomix.launcher.apps

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TvPagesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** A TV that opens the given actions (or components, in their short form) with the given package. */
    private fun resolver(vararg ok: Pair<String, String>): (Intent) -> String? = { i -> ok.toMap()[i.action ?: i.component!!.flattenToShortString()] }

    @Test fun listsOnlyPagesTheTvCanOpen_inOrder() {
        val r = resolver("android.settings.WIFI_SETTINGS" to "com.android.tv.settings", "android.settings.DEVICE_INFO_SETTINGS" to "com.android.tv.settings")
        assertEquals(listOf(TvPage.Network, TvPage.About), listedTvPages(r))
    }

    @Test fun leavesOutTheChooserAndGooglesStub() {
        val r = resolver("android.settings.WIFI_SETTINGS" to "android", "android.settings.SOUND_SETTINGS" to "com.google.android.tv.frameworkpackagestubs")
        assertEquals(emptyList<TvPage>(), listedTvPages(r))
    }

    @Test fun theNinePages_inTheOrderTheyAreListed() = assertEquals(
        listOf("Network & Internet", "Display & Sound", "Remotes & accessories", "Apps", "Storage", "Date & time", "Language", "Keyboard", "About this TV"),
        listedTvPages { "com.android.tv.settings" }.map { it.label })

    @Test fun dateAndTime_isOpenedByComponent() {
        val i = TvPage.DateTime.intent()
        assertEquals("com.android.tv.settings/.system.DateTimeActivity", i.component!!.flattenToShortString())
        assertNull(i.action)
    }

    @Test fun everyPageOpensInAFreshTask() = TvPage.entries.forEach {
        val want = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(it.name, want, it.intent().flags and want)
    }

    @Test fun neverNamesTheFactoryResetOrPairingPages() = TvPage.entries.forEach {
        assertFalse(it.name, (it.action + it.component).contains("FactoryReset"))
        assertNotEquals("com.google.android.intent.action.CONNECT_INPUT", it.action)
    }

    @Test fun onlyRemotesCarriesANote_sayingWhereToGo() {
        assertEquals("Opens TV settings. Choose Remotes & Accessories there.", TvPage.Remotes.note)
        assertEquals(listOf(TvPage.Remotes), TvPage.entries.filter { it.note != null })
    }

    @Test fun openTvPage_startsThePagesIntent() {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "tv.settings"; name = "Page" } }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent("android.settings.SOUND_SETTINGS"), info)
        shadowOf(app).checkActivities(true)
        assertTrue(app.openTvPage(TvPage.DisplaySound))
        assertEquals("android.settings.SOUND_SETTINGS", shadowOf(app).nextStartedActivity.action)
    }

    @Test fun openTvPage_aPageTheTvCannotOpen_isFalse_notACrash() {
        shadowOf(app).checkActivities(true)
        assertFalse(app.openTvPage(TvPage.Language))
    }

    @Test fun networkWords() {
        assertEquals("Ethernet", networkLabel(connected = true, wifi = true, ethernet = true))
        assertEquals("Wi-Fi", networkLabel(connected = true, wifi = true, ethernet = false))
        assertEquals("Connected", networkLabel(connected = true, wifi = false, ethernet = false))
        assertEquals("Not connected", networkLabel(connected = false, wifi = true, ethernet = false))
    }

    @Test fun freeSpaceWords() {
        assertEquals("1.2 GB free", freeSpaceLabel(1_200_000_000))
        assertEquals("12 GB free", freeSpaceLabel(12_340_000_000))
        assertEquals("850 MB free", freeSpaceLabel(850_000_000))
        assertEquals("0 MB free", freeSpaceLabel(0))
        assertNull(freeSpaceLabel(null))
        assertNull(freeSpaceLabel(-1))
    }
}
