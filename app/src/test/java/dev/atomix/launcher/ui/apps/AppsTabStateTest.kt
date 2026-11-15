package dev.atomix.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.DEFAULT_DOCK
import dev.atomix.launcher.prefs.HiddenApps
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.RowStore
import dev.atomix.launcher.shell.drawnApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private lateinit var favourites: RowStore
    private lateinit var catalog: AppCatalog
    private lateinit var hidden: HiddenApps
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val apps = (DEFAULT_DOCK + "com.other").map(::app)
    /** What Home's "Your apps" draws. */
    private fun onHome() = drawnApps(favourites.rows.value.first { it.id == ROW_APPS }, apps, hidden::isHidden).map { it.packageName }

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("apps_state_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        val prefs = LauncherPrefs(sp) { DEFAULT_DOCK }
        hidden = HiddenApps(prefs)
        favourites = RowStore(prefs)
        val now = UnconfinedTestDispatcher()
        catalog = AppCatalog({ apps }, CoroutineScope(now), now)
    }

    @Test fun list_isTheCatalog() {
        assertEquals(apps.map { it.packageName }, AppsTabState(favourites, hidden, catalog).list().map { it.packageName })
    }

    @Test fun toggle_addsAtTheEnd_thenRemoves() {
        val state = AppsTabState(favourites, hidden, catalog)
        assertFalse(state.isFavourite("com.other"))
        state.toggle("com.other")
        assertTrue(state.isFavourite("com.other"))
        assertEquals("com.other", onHome().last())
        state.toggle("com.other")
        assertFalse(state.isFavourite("com.other"))
        assertEquals(DEFAULT_DOCK, onHome())
    }

    @Test fun shown_filtersToTheRow_keepingListOrder() {
        val state = AppsTabState(favourites, hidden, catalog)
        assertEquals(apps.map { it.packageName }, state.shown(AppFilter.All).map { it.packageName })
        assertEquals(DEFAULT_DOCK, state.shown(AppFilter.InRow).map { it.packageName })
        assertEquals("All · 6", state.pillLabel(AppFilter.All))
        assertEquals("In this row · 5", state.pillLabel(AppFilter.InRow))
    }

    @Test fun menu_offersAddOrRemove_thenAppInfo() {
        val state = AppsTabState(favourites, hidden, catalog)
        assertEquals(listOf(AppMenuItem.Add, AppMenuItem.Hide, AppMenuItem.Info), state.menuFor("com.other"))
        assertEquals(listOf(AppMenuItem.Remove, AppMenuItem.Hide, AppMenuItem.Info), state.menuFor(DEFAULT_DOCK[0]))
    }

    @Test fun menu_forAnAppThatHasGone_isEmpty() =
        assertEquals(emptyList<AppMenuItem>(), AppsTabState(favourites, hidden, catalog).menuFor("uninstalled.pkg"))

    @Test fun focusAfterRemove_staysInTheSameRow_soTheTileItLandsOnIsNotRebuilt() {
        val eight = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        assertEquals("b", focusAfterRemove(eight, at = 0, columns = 7))   // the next tile slides into its place
        assertEquals("f", focusAfterRemove(eight, at = 6, columns = 7))   // not "h": that one changes row
        assertEquals("g", focusAfterRemove(eight, at = 7, columns = 7))   // alone in its row: the row above stays put
        assertEquals("d", focusAfterRemove(eight, at = 2, columns = 7))
    }

    @Test fun focusAfterRemove_theLastAppLeft_hasNoTileToGoTo() {
        assertNull(focusAfterRemove(listOf("a"), at = 0, columns = 7))
        assertNull(focusAfterRemove(listOf("a", "b"), at = 5, columns = 7))
    }

    @Test fun editIsReachedFromTheLastTileOfTheTopRow_howeverShortTheRow() {
        assertTrue(isUnderEdit(column = 6, topRowSize = 7))
        assertFalse(isUnderEdit(column = 5, topRowSize = 7))
        assertTrue(isUnderEdit(column = 2, topRowSize = 3))   // three apps: there is no seventh column
        assertTrue(isUnderEdit(column = 0, topRowSize = 1))
    }

    @Test fun moreApps_onlyWhileARowSitsBelow() {
        assertEquals("More apps below", moreAppsLine(rows = 5, first = 0, visible = 4))
        assertNull(moreAppsLine(rows = 5, first = 1, visible = 4))
        assertNull(moreAppsLine(rows = 4, first = 0, visible = 4))
        assertNull(moreAppsLine(rows = 0, first = 0, visible = 4))
    }

    @Test fun appsPage_leavesHiddenOut() {
        val now = UnconfinedTestDispatcher()
        val withSystem = listOf("x", "mediatek.factorymenu.ui", "y").map(::app)
        val state = AppsTabState(favourites, hidden, AppCatalog({ withSystem }, CoroutineScope(now), now))
        assertEquals(listOf("x", "y"), state.visibleApps().map { it.packageName })
    }

    private fun withSystemEntry(): AppsTabState {
        val now = UnconfinedTestDispatcher()
        val list = apps.take(2) + app("mediatek.factorymenu.ui") + apps.drop(2)
        return AppsTabState(favourites, hidden, AppCatalog({ list }, CoroutineScope(now), now))
    }

    @Test fun all_listsHiddenAppsLast() {
        val state = withSystemEntry()
        assertEquals(apps.map { it.packageName } + "mediatek.factorymenu.ui", state.shown(AppFilter.All).map { it.packageName })
        assertEquals(listOf("mediatek.factorymenu.ui"), state.shown(AppFilter.Hidden).map { it.packageName })
    }

    @Test fun pills_countHidden() =
        assertEquals(listOf("All · 7", "In this row · 5", "Hidden · 1"), AppFilter.entries.map(withSystemEntry()::pillLabel))

    @Test fun menu_offersHideOrShow() {
        val state = AppsTabState(favourites, hidden, catalog)
        assertEquals(listOf(AppMenuItem.Add, AppMenuItem.Hide, AppMenuItem.Info), state.menuFor("com.other"))
        state.toggleHidden("com.other")
        assertTrue(state.isHidden("com.other"))
        assertEquals(listOf(AppMenuItem.Add, AppMenuItem.Show, AppMenuItem.Info), state.menuFor("com.other"))
    }

    @Test fun showing_bringsItBackToTheRow() {
        val state = AppsTabState(favourites, hidden, catalog)
        val second = DEFAULT_DOCK[1]
        state.toggleHidden(second)
        assertFalse(second in onHome())
        state.toggleHidden(second)
        assertEquals(DEFAULT_DOCK, onHome())
    }
}
