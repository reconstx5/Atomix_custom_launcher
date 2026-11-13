package dev.atomix.launcher.prefs

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FavouriteAppsTest {
    private lateinit var prefs: LauncherPrefs
    private lateinit var fav: FavouriteApps
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("fav_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        prefs = LauncherPrefs(sp) { null }
        fav = FavouriteApps(prefs) { listOf("a", "b", "c").map(::app) }
    }

    @Test fun visible_hidesUninstalled_butKeepsThemSaved() {
        prefs.favouriteApps = listOf("a", "gone", "b")
        assertEquals(listOf("a", "b"), fav.visible().map { it.packageName })
        assertEquals(listOf("a", "gone", "b"), prefs.favouriteApps)
    }

    @Test fun move_usesVisibleIndex_skippingHidden() {
        prefs.favouriteApps = listOf("a", "gone", "b", "c")
        fav.move("c", 0)
        assertEquals(listOf("c", "a", "b"), fav.visible().map { it.packageName })
    }

    @Test fun add_appendsOnce() {
        prefs.favouriteApps = listOf("a")
        fav.add("b"); fav.add("b")
        assertEquals(listOf("a", "b"), prefs.favouriteApps)
        assertTrue(fav.contains("b"))
    }

    @Test fun remove_dropsIt() {
        prefs.favouriteApps = listOf("a", "b")
        fav.remove("a")
        assertEquals(listOf("b"), prefs.favouriteApps)
        assertFalse(fav.contains("a"))
    }

    @Test fun setVisibleOrder_keepsHiddenInPlace() {
        prefs.favouriteApps = listOf("a", "gone", "b", "c")
        fav.setVisibleOrder(listOf("c", "a"))
        assertEquals(listOf("c", "gone", "a"), prefs.favouriteApps)
    }
}
