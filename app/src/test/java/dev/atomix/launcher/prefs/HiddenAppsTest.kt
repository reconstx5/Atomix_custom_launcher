package dev.atomix.launcher.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HiddenAppsTest {
    private lateinit var sp: SharedPreferences
    private lateinit var hidden: HiddenApps

    @Before fun setUp() {
        sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("hidden_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        hidden = HiddenApps(LauncherPrefs(sp) { null })
    }

    @Test fun untilChanged_theThreeSystemEntriesAreHidden() {
        assertEquals(DEFAULT_HIDDEN, hidden.all())
        assertTrue(hidden.isHidden("mediatek.factorymenu.ui"))
    }

    @Test fun firstChange_savesTheWholeList() {
        hidden.show("com.mediatek.tv.service")
        assertEquals(
            listOf("com.mstar.android.tv.disclaimercustomization", "mediatek.factorymenu.ui"),
            LauncherPrefs(sp) { null }.hiddenApps,
        )
    }

    @Test fun showingEverything_staysEmpty_notDefaults() {
        DEFAULT_HIDDEN.forEach(hidden::show)
        assertEquals(emptyList<String>(), LauncherPrefs(sp) { null }.hiddenApps)
    }

    @Test fun hideTwice_listsOnce() {
        hidden.hide("a"); hidden.hide("a")
        assertEquals(1, hidden.all().count { it == "a" })
    }
}
