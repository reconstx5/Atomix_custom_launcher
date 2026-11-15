package dev.atomix.launcher.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HiddenApps
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowStore
import dev.atomix.launcher.prefs.withApp
import kotlinx.coroutines.flow.StateFlow

/** What the Apps tab and manage apps need: every installed app, and which of Home's app rows each is in. */
class AppsTabState(private val rows: RowStore, private val hidden: HiddenApps, private val catalog: AppCatalog) {
    val apps: StateFlow<List<AppEntry>> get() = catalog.apps
    fun list(): List<AppEntry> = catalog.apps.value

    /**
     * The app row manage apps was opened for ("Choose apps" on that row), or null when it was opened for no row
     * ("All apps", the Apps page's "Edit"). Set by whoever opens manage apps.
     */
    var forRow by mutableStateOf<String?>(null)

    /** The row manage apps is for, while that row still exists. */
    private fun row(): HomeRowSpec? = forRow?.let { id -> appRows().firstOrNull { it.id == id } }
    private fun appRows(): List<HomeRowSpec> = rows.rows.value.filter { it.kind == RowKind.Apps }

    /** What the Apps page lists: every installed app but those hidden from Atomix TV, in the list's own order. */
    fun visibleApps(): List<AppEntry> = list().filterNot { hidden.isHidden(it.packageName) }

    fun isIn(pkg: String, rowId: String): Boolean = appRows().firstOrNull { it.id == rowId }?.apps.orEmpty().contains(pkg)

    /** Whether the app is in any of Home's app rows: the Apps page's house. */
    fun inAnyAppRow(pkg: String): Boolean = appRows().any { pkg in it.apps }

    /** Adds to the end of the row, or takes it out of that row. */
    fun toggle(pkg: String, rowId: String) { val add = !isIn(pkg, rowId); rows.update { it.withApp(rowId, pkg, add) } }

    fun isHidden(pkg: String): Boolean = hidden.isHidden(pkg)

    /** Hides the app from Atomix TV, or shows it again. It stays in its rows' lists either way. */
    fun toggleHidden(pkg: String) = if (isHidden(pkg)) hidden.show(pkg) else hidden.hide(pkg)

    /** The heading: "Choose apps for “Tools”" for a row, "Manage apps" for none. */
    fun title(): String = row()?.let { "Choose apps for “${it.name}”" } ?: "Manage apps"

    /** The pills: "In this row" only when manage apps is for a row. */
    fun filters(): List<AppFilter> = if (row() != null) AppFilter.entries else listOf(AppFilter.All, AppFilter.Hidden)

    /**
     * The apps manage apps shows under [filter], in the list's own order. "All" puts the hidden apps after the others;
     * "In this row" is what the row shows on Home, so a hidden app is under "Hidden" only.
     */
    fun shown(filter: AppFilter): List<AppEntry> = when (filter) {
        AppFilter.All -> list().partition { !isHidden(it.packageName) }.let { (shown, hidden) -> shown + hidden }
        AppFilter.InRow -> row()?.let { r -> visibleApps().filter { it.packageName in r.apps } }.orEmpty()
        AppFilter.Hidden -> list().filter { isHidden(it.packageName) }
    }

    /** The filter's pill, with how many apps it holds: "All · 26", "In this row · 6", "Hidden · 3". */
    fun pillLabel(filter: AppFilter): String = when (filter) {
        AppFilter.All -> "All"
        AppFilter.InRow -> "In this row"
        AppFilter.Hidden -> "Hidden"
    } + " · " + shown(filter).size

    /**
     * What an app's menu offers (spec §8.2): add to or remove from the row manage apps is for, or "Add to a row…" when
     * it is for none; then hide or show; then app info. Nothing for an app that is no longer installed: its menu closes.
     */
    fun menuFor(pkg: String): List<AppMenuItem> {
        if (catalog.byPackage(pkg) == null) return emptyList()
        val r = row()
        return listOf(
            when {
                r == null -> AppMenuItem.AddToARow
                pkg in r.apps -> AppMenuItem.RemoveFrom(r.id, r.name)
                else -> AppMenuItem.AddTo(r.id, r.name)
            },
            if (isHidden(pkg)) AppMenuItem.Show else AppMenuItem.Hide,
            AppMenuItem.Info,
        )
    }

    /** The "Add to a row…" list: Home's app rows in order, each ticked when the app is in it. */
    fun rowTicks(pkg: String): List<Pair<HomeRowSpec, Boolean>> = appRows().map { it to (pkg in it.apps) }

    /** The tile's corner mark: for a row, a tick when the app is in it; for no row, a house when it is in any app row. */
    fun markFor(pkg: String): TileMark {
        val r = row()
        return when {
            r != null -> if (pkg in r.apps) TileMark.Tick else TileMark.None
            inAnyAppRow(pkg) -> TileMark.House
            else -> TileMark.None
        }
    }
}

/** The mark in a manage apps tile's top-right corner: one or the other, never both. */
enum class TileMark { None, Tick, House }

/**
 * Whether Up from this tile of the Apps top row goes to "Edit": the row's last tile, which is the seventh column only
 * when there are seven apps or more. With fewer, "Edit" would otherwise have no way to it.
 */
fun isUnderEdit(column: Int, topRowSize: Int): Boolean = column == topRowSize - 1

/**
 * Which tile takes focus when the tile at [at] is about to leave a grid of [columns]: one that will still be in the
 * same row afterwards, so it is not rebuilt (which would drop the focus it was just given). That is the next tile
 * when it shares the row, else the one before. Null when no tile is left.
 */
fun focusAfterRemove(shown: List<String>, at: Int, columns: Int): String? {
    if (at !in shown.indices || shown.size < 2) return null
    val next = at + 1
    return if (next < shown.size && next / columns == at / columns) shown[next] else shown.getOrNull(at - 1) ?: shown[next]
}

/** Which apps manage apps lists. */
enum class AppFilter { All, InRow, Hidden }

/** One line of an app's menu in manage apps. */
sealed interface AppMenuItem {
    val label: String
    data class AddTo(val row: String, val name: String) : AppMenuItem { override val label get() = "Add to $name" }
    data class RemoveFrom(val row: String, val name: String) : AppMenuItem { override val label get() = "Remove from $name" }
    data object AddToARow : AppMenuItem { override val label get() = "Add to a row…" }
    data object Hide : AppMenuItem { override val label get() = "Hide from Atomix TV" }
    data object Show : AppMenuItem { override val label get() = "Show in Atomix TV" }
    data object Info : AppMenuItem { override val label get() = "App info" }
}

/** The Apps page's "more below" line: shown while a row of apps sits under the [visible] rows that show from [first]. */
fun moreAppsLine(rows: Int, first: Int, visible: Int): String? = if (first + visible < rows) "More apps below" else null
