package dev.atomix.launcher.shell

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_INPUTS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeRowsModelTest {
    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, null, ColorDrawable(), Intent())
    private val installed = listOf(app("x", "b"), app("y", "A"), app("z", "c")).associateBy { it.packageName }
    private val onNow = HomeRowSpec(ROW_ON_NOW, RowKind.OnNow)
    private val inputs = HomeRowSpec(ROW_INPUTS, RowKind.Inputs)
    private fun ids(rows: List<HomeRowSpec>, hidden: Set<String> = emptySet(), inputCount: Int = 0) =
        drawnRows(rows, installed::get, { it in hidden }, inputCount).map { it.spec.id }

    @Test fun emptyFirstAppRow_isStillDrawn_otherEmptyRowsAreNot() {
        val rows = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x")), onNow, HomeRowSpec("row-1", RowKind.Apps), inputs)
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW), ids(rows, hidden = setOf("x")))
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW, ROW_INPUTS), ids(rows, inputCount = 5))
    }

    @Test fun firstShownAppRow_isTheOneKept_whenTheFirstIsHidden() {
        val rows = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x"), shown = false), onNow, HomeRowSpec("row-1", RowKind.Apps))
        assertEquals(listOf(ROW_ON_NOW, "row-1"), ids(rows))
    }

    @Test fun aToZ_sortsByName_ignoringCase() {
        val row = HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x", "y", "z"), sort = RowSort.AtoZ)
        assertEquals(listOf("A", "b", "c"), drawnRows(listOf(row, onNow), installed::get, { false }, 0).first().apps.map { it.label })
        assertEquals(listOf("b", "A", "c"), drawnRows(listOf(row.copy(sort = RowSort.YourOrder), onNow), installed::get, { false }, 0).first().apps.map { it.label })
    }

    @Test fun uninstalledApps_areNotDrawn() {
        val row = HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("gone", "z"))
        assertEquals(listOf("z"), drawnRows(listOf(row, onNow), installed::get, { false }, 0).first().apps.map { it.packageName })
    }

    @Test fun hiddenRows_areNotDrawn_andNeighboursSkipThem() {
        val rows = listOf(HomeRowSpec(ROW_APPS, RowKind.Apps, apps = listOf("x")), onNow.copy(shown = false), HomeRowSpec("row-1", RowKind.Apps, apps = listOf("y")))
        val drawn = ids(rows)
        assertEquals(listOf(ROW_APPS, "row-1"), drawn)
        assertEquals("row-1", rowBelow(ROW_APPS, drawn))
        assertEquals(ROW_APPS, rowAbove("row-1", drawn))
        assertNull(rowAbove(ROW_APPS, drawn))
        assertNull(rowBelow("row-1", drawn))
        assertNull(rowAbove(ROW_ON_NOW, drawn))
        assertNull(rowBelow(ROW_ON_NOW, drawn))
    }
}
