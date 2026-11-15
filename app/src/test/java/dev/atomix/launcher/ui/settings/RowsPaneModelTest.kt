package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_INPUTS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import dev.atomix.launcher.prefs.base
import dev.atomix.launcher.prefs.withRowAdded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RowsPaneModelTest {
    private val app = HomeRowSpec(ROW_APPS, RowKind.Apps, "Your apps")
    private val onNow = HomeRowSpec(ROW_ON_NOW, RowKind.OnNow)
    private val inputs = HomeRowSpec(ROW_INPUTS, RowKind.Inputs)

    @Test fun options_appRow_onNow_inputs() {
        val r = base().withRowAdded("Tools")
        assertEquals(listOf(RowOption.ChooseApps, RowOption.MoveDown, RowOption.Sort, RowOption.Delete), optionsFor(r, ROW_APPS))
        assertEquals(listOf(RowOption.MoveUp, RowOption.MoveDown), optionsFor(r, ROW_ON_NOW))
        assertEquals(listOf(RowOption.MoveUp, RowOption.MoveDown), optionsFor(r, ROW_INPUTS))   // Tools sits after Inputs
        assertEquals(listOf(RowOption.MoveUp), optionsFor(base(), ROW_INPUTS))
        assertFalse(RowOption.Delete in optionsFor(base(), ROW_APPS))
        assertEquals(listOf(RowOption.ChooseApps, RowOption.MoveUp, RowOption.Sort, RowOption.Delete), optionsFor(r, "row-1"))
    }

    @Test fun summaries() {
        assertEquals("6 apps", rowSummary(app, 6, 0, 0))
        assertEquals("1 app", rowSummary(app, 1, 0, 0))
        assertEquals("0 apps", rowSummary(app, 0, 0, 0))
        assertEquals("3 channels", rowSummary(onNow, 0, 3, 0))
        assertEquals("1 channel", rowSummary(onNow, 0, 1, 0))
        assertEquals("5 inputs", rowSummary(inputs, 0, 0, 5))
        assertEquals("3 hidden", hiddenSummary(3))
        assertEquals("None hidden", hiddenSummary(0))
    }

    @Test fun sortLabel() {
        assertEquals("Sort: A to Z", optionLabel(RowOption.Sort, app.copy(sort = RowSort.AtoZ)))
        assertEquals("Sort: your order", optionLabel(RowOption.Sort, app))
        assertEquals("Move up", optionLabel(RowOption.MoveUp, app))
    }

    @Test fun deleteAsks_inTheBoardsWords() {
        assertEquals("Delete “Tools”? The apps stay installed.", deleteQuestion("Tools"))
        assertEquals("One app row must stay on Home", KEEP_ONE_NOTE)
    }

    // After a delete, focus goes to the entry that took the row's place, or the one before when it was last.
    @Test fun afterDelete_focusGoesToTheEntryInItsPlace() {
        val r = base().withRowAdded("Tools")
        assertEquals(ROW_INPUTS, focusAfterDelete(r, ROW_ON_NOW))
        assertEquals(ROW_INPUTS, focusAfterDelete(r, "row-1"))
    }

    // After a move, focus stays on the same option, or the other move once the row reaches an end.
    @Test fun afterMove_focusStaysOnTheMove_orTheOtherOne() {
        val r = base()                                                     // Your apps, On now, Inputs
        assertEquals(RowOption.MoveDown, focusAfterMove(r, ROW_ON_NOW, RowOption.MoveUp))   // On now is first now
        assertEquals(RowOption.MoveDown, focusAfterMove(r, ROW_APPS, RowOption.MoveDown))   // Your apps is in the middle
        assertEquals(RowOption.MoveUp, focusAfterMove(r, ROW_ON_NOW, RowOption.MoveDown))   // On now is last now
    }

    // The pane follows focus: the focused entry and its options are brought whole into the view.
    @Test fun paneScroll_showsTheFocusedEntryWhole() {
        assertEquals(0, paneScroll(current = 0, top = 100, bottom = 300, viewH = 700))
        assertEquals(100, paneScroll(current = 0, top = 600, bottom = 800, viewH = 700))
        assertEquals(50, paneScroll(current = 100, top = 50, bottom = 150, viewH = 700))
        assertEquals(0, paneScroll(current = 100, top = -10, bottom = 80, viewH = 700))
    }

    @Test fun entryTops_openEntryIsTallerByItsOptions() {
        assertEquals(listOf(36, 140, 244), entryTops(entries = 3, open = null))
        assertEquals(listOf(36, 140 + OPTIONS_H, 244 + OPTIONS_H), entryTops(entries = 3, open = 0))
    }
}
