package dev.atomix.launcher.rows

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.ATOMIX_APP_KEY
import dev.atomix.launcher.prefs.DEFAULT_HIDDEN
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.MAX_APP_ROWS
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_INPUTS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RowsAndAppsTest {
    private lateinit var sp: SharedPreferences
    private val now = UnconfinedTestDispatcher()
    private lateinit var catalog: AppCatalog
    private var installed = listOf(app("a"), app("b"), app("c"))

    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, null, ColorDrawable(), Intent())

    private fun make(dock: () -> List<String>? = { null }): RowsAndApps {
        catalog = AppCatalog({ installed }, CoroutineScope(now), now)
        return RowsAndApps(LauncherPrefs(sp, dock), catalog, CoroutineScope(now))
    }

    /** Phase 2's Home with "Your apps" a, b. */
    private fun yours(): RowsAndApps {
        sp.edit().putString("favourite_apps", "a\nb").commit()
        return make()
    }

    private fun RowsAndApps.drawn(id: String = ROW_APPS) = view.value.drawnApps(id).map { it.packageName }

    @Before fun setUp() {
        sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("rows_and_apps_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
    }

    // --- The rows, as RowStore kept them ---

    @Test fun firstRead_buildsFromOldKeys_andLeavesThemAlone() {
        sp.edit().putString("favourite_apps", "a\nb").putString("home_row_order", "OnNowFirst").commit()
        val rows = make()
        assertEquals(listOf(ROW_ON_NOW, ROW_APPS, ROW_INPUTS), rows.view.value.rows.map { it.id })
        assertEquals(listOf("a", "b"), rows.view.value.rows.first { it.id == ROW_APPS }.apps)
        rows.setInRow("c", ROW_APPS, true)
        assertEquals("a\nb", sp.getString("favourite_apps", null))
        assertEquals("OnNowFirst", sp.getString("home_row_order", null))
    }

    @Test fun editRows_savesAndPublishes() {
        val rows = yours()
        rows.addRow("Tools")
        assertEquals(4, rows.view.value.rows.size)
        assertEquals(4, make().view.value.rows.size)
    }

    @Test fun unreadableSavedRows_areRebuiltFromOldKeys_neverAnEmptyHome() {
        sp.edit().putString("home_rows", "{").putString("favourite_apps", "a").commit()
        assertEquals(listOf("a"), make().view.value.rows.first().apps)
    }

    // Before phase 2's favourites were ever saved: the old dock, Atomix's own tile first, as phase 2's first start did.
    @Test fun noOldKeys_startsFromTheDock_withoutWritingTheOldKey() {
        val rows = make { listOf("x") }
        assertEquals(listOf(ATOMIX_APP_KEY, "x"), rows.view.value.rows.first().apps)
        assertEquals(null, sp.getString("favourite_apps", null))
    }

    // --- Hidden apps, as HiddenApps kept them ---

    @Test fun untilChanged_theThreeSystemEntriesAreHidden() {
        val rows = yours()
        DEFAULT_HIDDEN.forEach { assertTrue(rows.view.value.isHidden(it)) }
        assertTrue(rows.view.value.isHidden("mediatek.factorymenu.ui"))
    }

    @Test fun firstChange_savesTheWholeList() {
        yours().setHidden("com.mediatek.tv.service", false)
        assertEquals(
            listOf("com.mstar.android.tv.disclaimercustomization", "mediatek.factorymenu.ui"),
            LauncherPrefs(sp) { null }.hiddenApps,
        )
    }

    @Test fun showingEverything_staysEmpty_notDefaults() {
        val rows = yours()
        DEFAULT_HIDDEN.forEach { rows.setHidden(it, false) }
        assertEquals(emptyList<String>(), LauncherPrefs(sp) { null }.hiddenApps)
    }

    @Test fun hideTwice_listsOnce() {
        val rows = yours()
        rows.setHidden("a", true); rows.setHidden("a", true)
        assertEquals(1, LauncherPrefs(sp) { null }.hiddenApps.count { it == "a" })
    }

    // --- One hidden count: installed and hidden ---

    @Test fun hiddenApps_leaveOutUninstalled_andComeBackOnReinstall() {
        val rows = yours()
        rows.setHidden("b", true); rows.setHidden("gone.pkg", true)
        assertEquals(listOf("b"), rows.view.value.hiddenApps.map { it.packageName })
        assertTrue(rows.view.value.isHidden("gone.pkg"))
        installed = installed + app("gone.pkg"); catalog.invalidate()
        assertEquals(listOf("b", "gone.pkg"), rows.view.value.hiddenApps.map { it.packageName })
    }

    @Test fun visibleApps_areInstalledAndNotHidden_inListOrder() {
        val rows = yours()
        rows.setHidden("b", true)
        assertEquals(listOf("a", "c"), rows.view.value.visibleApps.map { it.packageName })
    }

    // --- Changes reach the view at once ---

    @Test fun hide_reachesTheView_andShowPutsItBackInPlace() {
        val rows = yours()
        rows.setHidden("a", true)
        assertEquals(listOf("b"), rows.drawn())
        rows.setHidden("a", false)
        assertEquals(listOf("a", "b"), rows.drawn())
    }

    @Test fun install_reachesTheView() {
        sp.edit().putString("favourite_apps", "a\nb\nx").commit()
        val rows = make()
        assertEquals(listOf("a", "b"), rows.drawn())
        installed = installed + app("x"); catalog.invalidate()
        assertEquals(listOf("a", "b", "x"), rows.drawn())
    }

    @Test fun addRow_returnsTheIdOfTheRowItMade() {
        val rows = yours()
        assertEquals("row-1", rows.addRow("Tools"))
        assertEquals("row-1" to "Tools", rows.view.value.rows.last().let { it.id to it.name })
    }

    @Test fun addRow_atTheLimit_returnsNull_andChangesNothing() {
        val rows = yours()
        repeat(MAX_APP_ROWS - 1) { assertNotNull(rows.addRow("R$it")) }
        val before = rows.view.value.rows
        assertNull(rows.addRow("One too many"))
        assertEquals(before, rows.view.value.rows)
    }

    @Test fun setInRow_addsAtTheEnd_thenTakesOut() {
        val rows = yours()
        rows.setInRow("c", ROW_APPS, true)
        assertEquals(listOf("a", "b", "c"), rows.drawn())
        rows.setInRow("c", ROW_APPS, false)
        assertEquals(listOf("a", "b"), rows.drawn())
    }

    @Test fun saveAppOrder_keepsHiddenAndUninstalledSlots() {
        sp.edit().putString("favourite_apps", "b\na\ngone").commit()
        val rows = make()
        rows.setHidden("c", true)
        rows.saveAppOrder(ROW_APPS, listOf("a"))
        assertEquals(listOf("a"), rows.drawn())
        assertEquals(listOf("a", "gone"), rows.view.value.rows.first { it.id == ROW_APPS }.apps)
    }

    @Test fun saveAppOrder_touchesOnlyThatRow() {
        sp.edit().putString("favourite_apps", "b\na").commit()
        val rows = make()
        val tools = rows.addRow("Tools")!!
        rows.setInRow("a", tools, true); rows.setInRow("b", tools, true)
        rows.saveAppOrder(tools, listOf("b", "a"))
        assertEquals(listOf("b", "a"), rows.drawn(tools))
        assertEquals(listOf("b", "a"), rows.drawn())
        rows.saveAppOrder(ROW_APPS, listOf("a", "b"))
        assertEquals(listOf("b", "a"), rows.drawn(tools))
        assertEquals(listOf("a", "b"), rows.drawn())
    }

    @Test fun inAnyAppRow_andAppRows_leaveOutOnNowAndInputs() {
        val rows = yours()
        val tools = rows.addRow("Tools")!!
        assertFalse(rows.view.value.inAnyAppRow("c"))
        rows.setInRow("c", tools, true)
        assertTrue(rows.view.value.inAnyAppRow("c"))
        assertEquals(listOf(ROW_APPS, tools), rows.view.value.appRows.map { it.id })
        assertEquals(emptyList<String>(), rows.drawn(ROW_ON_NOW))
        assertEquals(emptyList<String>(), rows.drawn("no-such-row"))
    }

    // --- What Home draws (the rules HomeRowsModel had) ---

    private val onNow = HomeRowSpec(ROW_ON_NOW, RowKind.OnNow)
    private val inputs = HomeRowSpec(ROW_INPUTS, RowKind.Inputs)

    /** Rows and apps holding exactly [list], over installed x ("b"), y ("A"), z ("c"), with [hidden] hidden. */
    private fun holding(list: List<HomeRowSpec>, hidden: Set<String> = emptySet()): RowsAndApps {
        installed = listOf(app("x", "b"), app("y", "A"), app("z", "c"))
        return yours().apply { editRows { list }; hidden.forEach { setHidden(it, true) } }
    }

    private fun RowsAndApps.ids(inputCount: Int = 0) = view.value.drawnRows(inputCount).map { it.spec.id }

    @Test fun emptyFirstAppRow_isStillDrawn_otherEmptyRowsAreNot() {
        val list = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x")), onNow, HomeRowSpec("row-1", RowKind.Apps), inputs)
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW), holding(list, hidden = setOf("x")).ids())
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW, ROW_INPUTS), holding(list).ids(inputCount = 5))
    }

    @Test fun shownInputsRow_isDrawn_withItsCount() {
        val list = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x")), onNow, inputs)
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW, ROW_INPUTS), holding(list).ids(inputCount = 5))
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW), holding(list).ids(inputCount = 0))
        val hiddenInputs = list.map { if (it.kind == RowKind.Inputs) it.copy(shown = false) else it }
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW), holding(hiddenInputs).ids(inputCount = 5))
    }

    @Test fun firstShownAppRow_isTheOneKept_whenTheFirstIsHidden() {
        val list = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x"), shown = false), onNow, HomeRowSpec("row-1", RowKind.Apps))
        assertEquals(listOf(ROW_ON_NOW, "row-1"), holding(list).ids())
    }

    @Test fun aToZ_sortsByName_ignoringCase() {
        val row = HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x", "y", "z"), sort = RowSort.AtoZ)
        assertEquals(listOf("A", "b", "c"), holding(listOf(row, onNow)).view.value.drawnApps(ROW_APPS).map { it.label })
        assertEquals(listOf("b", "A", "c"), holding(listOf(row.copy(sort = RowSort.YourOrder), onNow)).view.value.drawnApps(ROW_APPS).map { it.label })
    }

    @Test fun uninstalledApps_areNotDrawn() {
        val row = HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("gone", "z"))
        assertEquals(listOf("z"), holding(listOf(row, onNow)).drawn())
    }

    @Test fun hiddenRows_areNotDrawn() {
        val list = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x")), onNow.copy(shown = false), HomeRowSpec("row-1", RowKind.Apps, apps = listOf("y")))
        assertEquals(listOf(ROW_APPS, "row-1"), holding(list).ids())
    }
}
