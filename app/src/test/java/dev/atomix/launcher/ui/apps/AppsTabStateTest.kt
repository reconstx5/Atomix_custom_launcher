package dev.atomix.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.DEFAULT_DOCK
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.rows.RowsAndApps
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
    private lateinit var prefs: LauncherPrefs
    private lateinit var rows: RowsAndApps
    private lateinit var catalog: AppCatalog
    private val now = UnconfinedTestDispatcher()
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val apps = (DEFAULT_DOCK + "com.other").map(::app)
    private var installed = apps

    /** Rows and apps over [list] as the installed apps, and manage apps' state over it. */
    private fun over(list: List<AppEntry>): AppsTabState {
        installed = list
        catalog = AppCatalog({ installed }, CoroutineScope(now), now)
        rows = RowsAndApps(prefs, catalog, CoroutineScope(now))
        return AppsTabState(rows)
    }

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("apps_state_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        prefs = LauncherPrefs(sp) { DEFAULT_DOCK }
    }

    @Test fun shown_filtersToTheRow_keepingListOrder() {
        val state = over(apps)
        state.forRow = ROW_APPS
        assertEquals(apps.map { it.packageName }, state.shown(AppFilter.All).map { it.packageName })
        assertEquals(DEFAULT_DOCK, state.shown(AppFilter.InRow).map { it.packageName })
        assertEquals("All · 6", state.pillLabel(AppFilter.All))
        assertEquals("In this row · 5", state.pillLabel(AppFilter.InRow))
    }

    @Test fun menu_offersAddOrRemove_thenAppInfo() {
        val state = over(apps)
        state.forRow = ROW_APPS
        assertEquals(listOf("Add to Your apps", "Hide from Atomix TV", "App info"), state.menuFor("com.other").map { it.label })
        assertEquals(listOf("Remove from Your apps", "Hide from Atomix TV", "App info"), state.menuFor(DEFAULT_DOCK[0]).map { it.label })
    }

    @Test fun menu_forAnAppThatHasGone_isEmpty() =
        assertEquals(emptyList<AppMenuItem>(), over(apps).menuFor("uninstalled.pkg"))

    // An app uninstalled while its menu is open: the menu has nothing left to offer, so it closes.
    @Test fun menuFor_afterTheCatalogLosesTheApp_isEmpty() {
        val state = over(apps)
        assertTrue(state.menuFor("com.other").isNotEmpty())
        installed = apps.filterNot { it.packageName == "com.other" }; catalog.invalidate()
        assertEquals(emptyList<AppMenuItem>(), state.menuFor("com.other"))
    }

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

    private fun withSystemEntry(): AppsTabState = over(apps.take(2) + app("mediatek.factorymenu.ui") + apps.drop(2))

    @Test fun all_listsHiddenAppsLast() {
        val state = withSystemEntry()
        assertEquals(apps.map { it.packageName } + "mediatek.factorymenu.ui", state.shown(AppFilter.All).map { it.packageName })
        assertEquals(listOf("mediatek.factorymenu.ui"), state.shown(AppFilter.Hidden).map { it.packageName })
    }

    @Test fun pills_countHidden() {
        val state = withSystemEntry()
        state.forRow = ROW_APPS
        assertEquals(listOf("All · 7", "In this row · 5", "Hidden · 1"), state.filters().map(state::pillLabel))
    }

    // The "Hidden" pill and Settings' count are the same number: installed hidden apps only.
    @Test fun hiddenPill_countsWhatTheViewCounts() {
        val state = over(apps)   // installed: DEFAULT_DOCK + com.other, none of DEFAULT_HIDDEN
        rows.setHidden("com.other", true); rows.setHidden("gone.pkg", true)
        assertEquals("Hidden · ${rows.view.value.hiddenApps.size}", state.pillLabel(AppFilter.Hidden))
        assertEquals("Hidden · 1", state.pillLabel(AppFilter.Hidden))
    }

    @Test fun menu_offersHideOrShow() {
        val state = over(apps)
        assertEquals(listOf(AppMenuItem.AddToARow, AppMenuItem.Hide, AppMenuItem.Info), state.menuFor("com.other"))
        rows.setHidden("com.other", true)
        assertEquals(listOf(AppMenuItem.AddToARow, AppMenuItem.Show, AppMenuItem.Info), state.menuFor("com.other"))
    }

    // Spec §8: opened for a row ("Choose apps" on Tools) or for no row ("All apps", the Apps page's "Edit").
    private val x = "com.other"
    private fun withTools(): AppsTabState = over(apps).also { rows.addRow("Tools") }

    @Test fun forARow_titleFiltersAndMenu() {
        val state = withTools()
        state.forRow = "row-1"
        assertEquals("Choose apps for “Tools”", state.title())
        assertEquals(listOf(AppFilter.All, AppFilter.InRow, AppFilter.Hidden), state.filters())
        assertEquals(listOf("Add to Tools", "Hide from Atomix TV", "App info"), state.menuFor(x).map { it.label })
        rows.setInRow(x, "row-1", true)
        assertEquals("Remove from Tools", state.menuFor(x).first().label)
    }

    @Test fun forNoRow_titleFiltersAndMenu() {
        val state = withTools()
        state.forRow = null
        assertEquals("Manage apps", state.title())
        assertEquals(listOf(AppFilter.All, AppFilter.Hidden), state.filters())
        assertEquals("Add to a row…", state.menuFor(x).first().label)
    }

    @Test fun marks_tickForTheRow_houseOtherwise_neverBoth() {
        val state = withTools()
        rows.setInRow(x, "row-1", true)
        state.forRow = ROW_APPS
        assertEquals(TileMark.None, state.markFor(x))
        state.forRow = "row-1"
        assertEquals(TileMark.Tick, state.markFor(x))
        state.forRow = null
        assertEquals(TileMark.House, state.markFor(x))
        rows.setInRow(x, "row-1", false)
        assertEquals(TileMark.None, state.markFor(x))
    }

    // Opened for a row that has since been deleted: it is manage apps for no row, marks included.
    @Test fun forAGoneRow_isForNoRow() {
        val state = withTools()
        state.forRow = "row-1"
        rows.editRows { list -> list.filterNot { it.id == "row-1" } }
        assertEquals("Manage apps", state.title())
        assertEquals(TileMark.House, state.markFor(DEFAULT_DOCK[0]))
    }

    @Test fun inThisRow_isThatRow() {
        val state = withTools()
        rows.setInRow(x, "row-1", true)
        state.forRow = "row-1"
        assertEquals(listOf(x), state.shown(AppFilter.InRow).map { it.packageName })
        assertEquals("In this row · 1", state.pillLabel(AppFilter.InRow))
    }
}
