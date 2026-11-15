package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import dev.atomix.launcher.prefs.canAddRow
import dev.atomix.launcher.prefs.canDelete
import dev.atomix.launcher.prefs.moved
import dev.atomix.launcher.ui.kit.LineIcon
import kotlin.math.max

/** What can be done to a row in Settings › Rows and apps, in the order its options show. */
enum class RowOption(val label: String) {
    ChooseApps("Choose apps"),
    Rename("Rename"),
    MoveUp("Move up"),
    MoveDown("Move down"),
    Sort("Sort"),
    Delete("Delete"),
}

/** Shown under the last shown app row when OK would hide it. */
const val KEEP_ONE_NOTE = "One app row must stay on Home"

/** Names the "All apps" entry where a row's id would go: what manage apps, opened from it, comes back to. */
const val ALL_APPS_ENTRY = "+all"

/** The options of row [id] of [rows] (spec §7.3). */
fun optionsFor(rows: List<HomeRowSpec>, id: String): List<RowOption> {
    val i = rows.indexOfFirst { it.id == id }
    if (i < 0) return emptyList()
    val moves = listOfNotNull(RowOption.MoveUp.takeIf { i > 0 }, RowOption.MoveDown.takeIf { i < rows.lastIndex })
    if (rows[i].kind != RowKind.Apps) return moves
    return listOf(RowOption.ChooseApps, RowOption.Rename) + moves + RowOption.Sort + listOfNotNull(RowOption.Delete.takeIf { rows.canDelete(id) })
}

/** Whether "New row" ends the list of rows: not once there are eight app rows. */
fun showsNewRow(rows: List<HomeRowSpec>): Boolean = rows.canAddRow()

/** An option's words on its button: Sort says which order the row has now. */
fun optionLabel(o: RowOption, spec: HomeRowSpec): String =
    if (o == RowOption.Sort) "Sort: " + if (spec.sort == RowSort.AtoZ) "A to Z" else "your order" else o.label

fun optionIcon(o: RowOption): LineIcon = when (o) {
    RowOption.ChooseApps -> LineIcon.Apps
    RowOption.Rename -> LineIcon.Edit
    RowOption.MoveUp -> LineIcon.ArrowUp
    RowOption.MoveDown -> LineIcon.ArrowDown
    RowOption.Sort -> LineIcon.Sort
    RowOption.Delete -> LineIcon.Trash
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** The line under a row's name: what Home would draw in it. */
fun rowSummary(spec: HomeRowSpec, apps: Int, channels: Int, inputs: Int): String = when (spec.kind) {
    RowKind.Apps -> count(apps, "app", "apps")
    RowKind.OnNow -> count(channels, "channel", "channels")
    RowKind.Inputs -> count(inputs, "input", "inputs")
}

fun hiddenSummary(n: Int): String = if (n == 0) "None hidden" else "$n hidden"

fun deleteQuestion(name: String): String = "Delete “$name”? The apps stay installed."

/** The entry that takes row [id]'s place once it is deleted: the next one, or the one before when it was last. */
fun focusAfterDelete(rows: List<HomeRowSpec>, id: String): String? {
    val i = rows.indexOfFirst { it.id == id }
    if (i < 0) return null
    return (rows.getOrNull(i + 1) ?: rows.getOrNull(i - 1))?.id
}

/** The option focus stays on once row [id] of [rows] makes move [o]: the same move, or the other once it reaches an end. */
fun focusAfterMove(rows: List<HomeRowSpec>, id: String, o: RowOption): RowOption {
    val after = rows.moved(id, if (o == RowOption.MoveUp) -1 else 1)
    return if (o in optionsFor(after, id)) o else if (o == RowOption.MoveUp) RowOption.MoveDown else RowOption.MoveUp
}

/** An entry is 92 high and 12 apart; the open one has its options under it (56, and 20 below them). */
const val ENTRY_H = 92
const val ENTRY_GAP = 12
const val OPTIONS_H = 76
private const val LABEL_H = 36

/** Where each of [entries] entries starts, under the group's label, with entry [open] showing its options. */
fun entryTops(entries: Int, open: Int?): List<Int> {
    var y = LABEL_H
    return List(entries) { i ->
        val top = y
        y += ENTRY_H + ENTRY_GAP + if (i == open) OPTIONS_H else 0
        top
    }
}

/** How far the pane is scrolled so that [top] to [bottom] shows whole in a view [viewH] high, moving as little as it can. */
fun paneScroll(current: Int, top: Int, bottom: Int, viewH: Int): Int {
    var s = current
    if (bottom - s > viewH) s = bottom - viewH
    if (top - s < 0) s = top
    return max(0, s)
}
