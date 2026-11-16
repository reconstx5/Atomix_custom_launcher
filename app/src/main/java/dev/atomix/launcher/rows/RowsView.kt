package dev.atomix.launcher.rows

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.shell.DrawnRow
import dev.atomix.launcher.shell.drawnApps
import dev.atomix.launcher.shell.drawnRows

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
