package dev.atomix.launcher.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.rows.RowsAndApps
import dev.atomix.launcher.rows.RowsView

/**
 * Manage apps' own screen logic (which row it is for, its heading, filters, menus and tile marks), read from Rows and
 * apps' current view. Every change goes through [RowsAndApps]; nothing here holds or changes data.
 */
class AppsTabState(private val rows: RowsAndApps) {
    private val view: RowsView get() = rows.view.value

    /**
     * The app row manage apps was opened for ("Choose apps" on that row), or null when it was opened for no row
     * ("All apps", the Apps page's "Edit"). Set by whoever opens manage apps.
     */
    var forRow by mutableStateOf<String?>(null)

    /** The row manage apps is for, while that row still exists. */
    private fun row(): HomeRowSpec? = forRow?.let { id -> view.appRows.firstOrNull { it.id == id } }

    /** The heading: "Choose apps for “Tools”" for a row, "Manage apps" for none. */
    fun title(): String = row()?.let { "Choose apps for “${it.name}”" } ?: "Manage apps"

    /** The pills: "In this row" only when manage apps is for a row. */
    fun filters(): List<AppFilter> = if (row() != null) AppFilter.entries else listOf(AppFilter.All, AppFilter.Hidden)

    /**
     * The apps manage apps shows under [filter], in the list's own order. "All" puts the hidden apps after the others;
     * "In this row" is what the row shows on Home, so a hidden app is under "Hidden" only.
     */
    fun shown(filter: AppFilter): List<AppEntry> = when (filter) {
        AppFilter.All -> view.visibleApps + view.hiddenApps
        AppFilter.InRow -> row()?.let { r -> view.visibleApps.filter { it.packageName in r.apps } }.orEmpty()
        AppFilter.Hidden -> view.hiddenApps
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
        if (view.byPackage(pkg) == null) return emptyList()
        val r = row()
        return listOf(
            when {
                r == null -> AppMenuItem.AddToARow
                pkg in r.apps -> AppMenuItem.RemoveFrom(r.id, r.name)
                else -> AppMenuItem.AddTo(r.id, r.name)
            },
            if (view.isHidden(pkg)) AppMenuItem.Show else AppMenuItem.Hide,
            AppMenuItem.Info,
        )
    }

    /** The tile's corner mark: for a row, a tick when the app is in it; for no row, a house when it is in any app row. */
    fun markFor(pkg: String): TileMark {
        val r = row()
        return when {
            r != null -> if (pkg in r.apps) TileMark.Tick else TileMark.None
            view.inAnyAppRow(pkg) -> TileMark.House
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
