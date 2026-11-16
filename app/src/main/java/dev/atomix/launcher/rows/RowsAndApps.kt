package dev.atomix.launcher.rows

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.decodeRows
import dev.atomix.launcher.prefs.encodeRows
import dev.atomix.launcher.prefs.firstRows
import dev.atomix.launcher.prefs.reordered
import dev.atomix.launcher.prefs.withApp
import dev.atomix.launcher.prefs.withRowAdded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Rows and apps: Home's rows, which apps are in each, which apps are hidden from Atomix TV, and the installed apps.
 * Every screen follows [view]; every change goes through the commands here and is in [view] as soon as it returns.
 *
 * The first read with nothing saved (or something unreadable) builds the rows from phase 2's settings and saves them at
 * once; phase 2's own keys are only read.
 */
class RowsAndApps(private val prefs: LauncherPrefs, private val catalog: AppCatalog, scope: CoroutineScope) {
    private var rows: List<HomeRowSpec> = decodeRows(prefs.homeRows)
        ?: firstRows(prefs.favouriteApps, prefs.legacyRowOrder).also { prefs.homeRows = encodeRows(it) }

    private val _view = MutableStateFlow(snapshot())
    val view: StateFlow<RowsView> = _view

    init {
        scope.launch { catalog.apps.collect { publish() } }
    }

    private fun snapshot() = RowsView(rows, catalog.apps.value, prefs.hiddenApps)
    private fun publish() { _view.value = snapshot() }

    /** Applies [edit] to the rows; saves and publishes only when something changed. */
    fun editRows(edit: (List<HomeRowSpec>) -> List<HomeRowSpec>) {
        val next = edit(rows)
        if (next == rows) return
        prefs.homeRows = encodeRows(next)
        rows = next
        publish()
    }

    /** Adds an empty app row called [name] at the end; the id of the row it made, or null when there is no room. */
    fun addRow(name: String): String? {
        val before = rows
        editRows { it.withRowAdded(name) }
        return if (rows.size > before.size) rows.last().id else null
    }

    /** Puts [pkg] at the end of the row, or takes it out of that row only. */
    fun setInRow(pkg: String, rowId: String, inRow: Boolean) = editRows { it.withApp(rowId, pkg, inRow) }

    /** Hides the app from Atomix TV, or shows it again; it keeps its place in its rows either way. */
    fun setHidden(pkg: String, hidden: Boolean) {
        val list = prefs.hiddenApps
        // Saved even when unchanged, as before: the first change saves the whole list and the defaults stop applying.
        prefs.hiddenApps = if (hidden) (if (pkg in list) list else list + pkg) else list - pkg
        publish()
    }

    /** The app row's new order after Edit on Home: an app it shows that is left out of [order] leaves that row only. */
    fun saveAppOrder(rowId: String, order: List<String>) {
        val shown = _view.value.drawnApps(rowId).map { it.packageName }.toSet()
        editRows { it.reordered(rowId, order) { pkg -> pkg in shown } }
    }
}
