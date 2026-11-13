package dev.atomix.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.DEFAULT_DOCK
import dev.atomix.launcher.prefs.FavouriteApps
import dev.atomix.launcher.prefs.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppsTabStateTest {
    private lateinit var favourites: FavouriteApps
    private lateinit var catalog: AppCatalog
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val apps = (DEFAULT_DOCK + "com.other").map(::app)

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("apps_state_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        favourites = FavouriteApps(LauncherPrefs(sp) { DEFAULT_DOCK }) { apps }
        val now = UnconfinedTestDispatcher()
        catalog = AppCatalog({ apps }, CoroutineScope(now), now)
    }

    @Test fun list_isTheCatalog() {
        assertEquals(apps.map { it.packageName }, AppsTabState(favourites, catalog).list().map { it.packageName })
    }

    @Test fun toggle_addsAtTheEnd_thenRemoves() {
        val state = AppsTabState(favourites, catalog)
        assertFalse(state.isFavourite("com.other"))
        state.toggle("com.other")
        assertTrue(state.isFavourite("com.other"))
        assertEquals("com.other", favourites.visible().last().packageName)
        state.toggle("com.other")
        assertFalse(state.isFavourite("com.other"))
        assertEquals(DEFAULT_DOCK, favourites.visible().map { it.packageName })
    }

    @Test fun shown_filtersToTheRow_keepingListOrder() {
        val state = AppsTabState(favourites, catalog)
        assertEquals(apps.map { it.packageName }, state.shown(AppFilter.All).map { it.packageName })
        assertEquals(DEFAULT_DOCK, state.shown(AppFilter.InRow).map { it.packageName })
        assertEquals("All · 6", state.pillLabel(AppFilter.All))
        assertEquals("In this row · 5", state.pillLabel(AppFilter.InRow))
    }

    @Test fun menu_offersAddOrRemove_thenAppInfo() {
        val state = AppsTabState(favourites, catalog)
        assertEquals(listOf(AppMenuItem.Add, AppMenuItem.Info), state.menuFor("com.other"))
        assertEquals(listOf(AppMenuItem.Remove, AppMenuItem.Info), state.menuFor(DEFAULT_DOCK[0]))
    }

    @Test fun menu_forAnAppThatHasGone_isEmpty() =
        assertEquals(emptyList<AppMenuItem>(), AppsTabState(favourites, catalog).menuFor("uninstalled.pkg"))
}
