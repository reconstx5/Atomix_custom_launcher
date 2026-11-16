package dev.atomix.launcher.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for Android's own org.json, which the rows are saved with.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeRowsTest {
    @Test fun firstStart_isPhase2sHome() {
        val r = firstRows(listOf("dev.atomix.launcher", "org.smarttube.stable"), RowOrder.AppsFirst)
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW, ROW_INPUTS), r.map { it.id })
        assertEquals(listOf("dev.atomix.launcher", "org.smarttube.stable"), r[0].apps)
        assertEquals("Your apps", r[0].name)
        assertEquals(listOf(true, true, false), r.map { it.shown })
        assertEquals(listOf(ROW_ON_NOW, ROW_APPS, ROW_INPUTS), firstRows(emptyList(), RowOrder.OnNowFirst).map { it.id })
    }

    @Test fun encodeThenDecode_isTheSame() {
        val r = firstRows(listOf("a"), RowOrder.AppsFirst).withRowAdded("Tools").withSort("row-1", RowSort.AtoZ)
        assertEquals(r, decodeRows(encodeRows(r)))
    }

    @Test fun brokenSavedRows_readAsAbsent() =
        listOf(null, "", "{", "[]", noAppRow, twoOnNow, repeatedId, unknownKind).forEach { assertNull(it, decodeRows(it)) }

    // Home keys a row's tiles by package: a package twice in one row would stop Home on every start.
    @Test fun samePackageTwiceInARow_isKeptOnce() {
        val saved = savedRows(
            """{"id":"apps","kind":"Apps","name":"Your apps","apps":["a","b","a"],"sort":"YourOrder","shown":true}""",
            """{"id":"onNow","kind":"OnNow","name":"","apps":[],"sort":"YourOrder","shown":true}""",
            """{"id":"inputs","kind":"Inputs","name":"","apps":[],"sort":"YourOrder","shown":false}""",
        )
        assertEquals(listOf("a", "b"), decodeRows(saved)!!.first().apps)
        assertEquals(listOf("a", "b"), firstRows(listOf("a", "b", "a"), RowOrder.AppsFirst).first().apps)
    }

    @Test fun longName_isCut() = assertEquals(24, decodeRows(withName("x".repeat(30)))!!.first().name.length)

    @Test fun move_staysInside() {
        val r = base()
        assertEquals(r, r.moved(ROW_APPS, -1))
        assertEquals(listOf(ROW_ON_NOW, ROW_APPS, ROW_INPUTS), r.moved(ROW_APPS, 1).map { it.id })
    }

    @Test fun lastShownAppRow_cannotBeHidden_onNowCan() {
        val r = base()
        assertFalse(r.canHide(ROW_APPS))
        assertEquals(r, r.withShown(ROW_APPS, false))
        assertTrue(r.canHide(ROW_ON_NOW))
    }

    @Test fun lastAppRow_cannotBeDeleted_andOnNowNever() {
        assertFalse(base().canDelete(ROW_APPS))
        assertTrue(base().withRowAdded("Tools").canDelete(ROW_APPS))
        assertFalse(base().canDelete(ROW_ON_NOW))
    }

    @Test fun eightAppRows_atMost() {
        var r = base()
        repeat(7) { r = r.withRowAdded("R$it") }
        assertFalse(r.canAddRow())
        assertEquals(r, r.withRowAdded("Ninth"))
    }

    @Test fun newIds_neverRepeat() {
        val r = base().withRowAdded("A").withRowAdded("B").deleted("row-1").withRowAdded("C")
        assertEquals(listOf("row-2", "row-3"), r.filter { it.id.startsWith("row-") }.map { it.id })
    }

    @Test fun sameAppInTwoRows_orderedSeparately() {
        val r = base().withRowAdded("Tools").withApp("row-1", "tvbro", true).withApp(ROW_APPS, "tvbro", true)
        val s = r.reordered("row-1", listOf("tvbro"), { true })
            .reordered(ROW_APPS, listOf("tvbro") + r.first().apps.filter { it != "tvbro" }, { true })
        assertEquals("tvbro", s.first { it.id == ROW_APPS }.apps.first())
        assertEquals(listOf("tvbro"), s.first { it.id == "row-1" }.apps)
        assertEquals(listOf("tvbro"), s.withApp(ROW_APPS, "tvbro", false).first { it.id == "row-1" }.apps)
    }

    // FavouriteApps' rule, carried over: an app not drawn (uninstalled or hidden) keeps its slot when the row is reordered.
    @Test fun reorder_keepsUndrawnAppsInTheirSlots() {
        val r = firstRows(listOf("a", "b", "c", "d"), RowOrder.AppsFirst)
        val s = r.reordered(ROW_APPS, listOf("d", "c", "a")) { it != "b" }
        assertEquals(listOf("d", "b", "c", "a"), s.first().apps)
    }

    @Test fun addingAnAppTwice_keepsOne() =
        assertEquals(listOf("a", "b", "c"), base().withApp(ROW_APPS, "c", true).withApp(ROW_APPS, "c", true).first().apps)

    @Test fun deletingTheLastShownAppRow_whileOthersAreHidden_isRefused() {
        val r = base().withRowAdded("Tools").withShown("row-1", false)
        assertFalse(r.canDelete(ROW_APPS))
        assertEquals(r, r.deleted(ROW_APPS))
        assertTrue(r.canDelete("row-1"))
    }
}
