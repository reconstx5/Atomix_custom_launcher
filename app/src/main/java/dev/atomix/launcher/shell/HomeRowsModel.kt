package dev.atomix.launcher.shell

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort

/** A row Home draws, with the apps it draws (empty for "On now" and Inputs). */
data class DrawnRow(val spec: HomeRowSpec, val apps: List<AppEntry>)

/**
 * What an app row draws: its apps that are installed ([byPkg]) and not hidden from Atomix TV, in the row's order, or by
 * name (ignoring case) when the row is sorted "A to Z".
 */
fun drawnApps(row: HomeRowSpec, byPkg: (String) -> AppEntry?, isHidden: (String) -> Boolean): List<AppEntry> {
    val apps = row.apps.filterNot(isHidden).mapNotNull(byPkg)
    return if (row.sort == RowSort.AtoZ) apps.sortedBy { it.label.lowercase() } else apps
}

fun drawnApps(row: HomeRowSpec, installed: List<AppEntry>, isHidden: (String) -> Boolean): List<AppEntry> =
    installed.associateBy { it.packageName }.let { byPkg -> drawnApps(row, byPkg::get, isHidden) }

/**
 * The rows Home draws: the shown ones, in order. An app row with no apps to draw is left out, except the first shown
 * app row, so Home always has its heading and "All apps". An Inputs row with no [inputs] is left out. "On now" stays:
 * its own area says when there is nothing to show.
 */
fun drawnRows(rows: List<HomeRowSpec>, byPkg: (String) -> AppEntry?, isHidden: (String) -> Boolean, inputs: Int): List<DrawnRow> {
    val firstAppRow = rows.firstOrNull { it.shown && it.kind == RowKind.Apps }?.id
    return rows.filter { it.shown }.mapNotNull { r ->
        when (r.kind) {
            RowKind.Apps -> drawnApps(r, byPkg, isHidden).let { apps -> DrawnRow(r, apps).takeIf { apps.isNotEmpty() || r.id == firstAppRow } }
            RowKind.OnNow -> DrawnRow(r, emptyList())
            RowKind.Inputs -> DrawnRow(r, emptyList()).takeIf { inputs > 0 }
        }
    }
}

/** The drawn row over [id], or null when it is the first (Up then goes to the top bar) or not drawn. */
fun rowAbove(id: String, drawn: List<String>): String? = drawn.indexOf(id).takeIf { it > 0 }?.let { drawn[it - 1] }

/** The drawn row under [id], or null when it is the last (Down then goes nowhere) or not drawn. */
fun rowBelow(id: String, drawn: List<String>): String? = drawn.indexOf(id).takeIf { it >= 0 }?.let { drawn.getOrNull(it + 1) }
