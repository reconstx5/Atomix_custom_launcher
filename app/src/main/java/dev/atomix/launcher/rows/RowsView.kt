package dev.atomix.launcher.rows

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort

/**
 * Rows and apps at one moment: every row of Home, every installed app (in the list's own order) and the saved hidden
 * list. A hidden app is one that is installed and hidden; the saved list also keeps uninstalled ones, so an app
 * installed again comes back hidden.
 */
data class RowsView(val rows: List<HomeRowSpec>, val installed: List<AppEntry>, private val hiddenSaved: List<String>) {
    private val hiddenSet = hiddenSaved.toSet()
    private val byPkg = installed.associateBy { it.packageName }

    /** Whether the app is on the saved hidden list, installed or not. */
    fun isHidden(pkg: String): Boolean = pkg in hiddenSet

    /** What the Apps page lists: installed apps that are not hidden. */
    val visibleApps: List<AppEntry> = installed.filterNot { isHidden(it.packageName) }

    /** The hidden apps: installed and hidden. Every count of hidden apps is this list's size. */
    val hiddenApps: List<AppEntry> = installed.filter { isHidden(it.packageName) }

    /** Home's app rows, in order (not On now or Inputs). */
    val appRows: List<HomeRowSpec> = rows.filter { it.kind == RowKind.Apps }

    fun byPackage(pkg: String): AppEntry? = byPkg[pkg]

    /** What the app row [rowId] shows; empty for a row that is not an app row or no longer exists. */
    fun drawnApps(rowId: String): List<AppEntry> =
        appRows.firstOrNull { it.id == rowId }?.let { drawnApps(it, byPkg::get, ::isHidden) }.orEmpty()

    /** Whether the app is in any of Home's app rows. */
    fun inAnyAppRow(pkg: String): Boolean = appRows.any { pkg in it.apps }

    /** The rows Home draws, given how many entries the Inputs row holds. */
    fun drawnRows(inputs: Int): List<DrawnRow> = drawnRows(rows, byPkg::get, ::isHidden, inputs)
}

/** A row Home draws, with the apps it draws (empty for "On now" and Inputs). */
data class DrawnRow(val spec: HomeRowSpec, val apps: List<AppEntry>)

/**
 * What an app row draws: its apps that are installed ([byPkg]) and not hidden from Atomix TV, in the row's order, or by
 * name (ignoring case) when the row is sorted "A to Z".
 */
private fun drawnApps(row: HomeRowSpec, byPkg: (String) -> AppEntry?, isHidden: (String) -> Boolean): List<AppEntry> {
    val apps = row.apps.filterNot(isHidden).mapNotNull(byPkg)
    return if (row.sort == RowSort.AtoZ) apps.sortedBy { it.label.lowercase() } else apps
}

/**
 * The rows Home draws: the shown ones, in order. An app row with no apps to draw is left out, except the first shown
 * app row, so Home always has its heading and "All apps". An Inputs row with no [inputs] is left out. "On now" stays:
 * its own area says when there is nothing to show.
 */
private fun drawnRows(rows: List<HomeRowSpec>, byPkg: (String) -> AppEntry?, isHidden: (String) -> Boolean, inputs: Int): List<DrawnRow> {
    val firstAppRow = rows.firstOrNull { it.shown && it.kind == RowKind.Apps }?.id
    return rows.filter { it.shown }.mapNotNull { r ->
        when (r.kind) {
            RowKind.Apps -> drawnApps(r, byPkg, isHidden).let { apps -> DrawnRow(r, apps).takeIf { apps.isNotEmpty() || r.id == firstAppRow } }
            RowKind.OnNow -> DrawnRow(r, emptyList())
            RowKind.Inputs -> DrawnRow(r, emptyList()).takeIf { inputs > 0 }
        }
    }
}
