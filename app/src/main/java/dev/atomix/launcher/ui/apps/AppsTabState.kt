package dev.atomix.launcher.ui.apps

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.FavouriteApps
import dev.atomix.launcher.prefs.HiddenApps
import kotlinx.coroutines.flow.StateFlow

/** What the Apps tab and manage apps need: every installed app, and whether each is in Home's "Your apps" row. */
class AppsTabState(private val favourites: FavouriteApps, private val hidden: HiddenApps, private val catalog: AppCatalog) {
    val apps: StateFlow<List<AppEntry>> get() = catalog.apps
    fun list(): List<AppEntry> = catalog.apps.value

    /** What the Apps page lists: every installed app but those hidden from Atomix TV, in the list's own order. */
    fun visibleApps(): List<AppEntry> = list().filterNot { hidden.isHidden(it.packageName) }
    fun isFavourite(pkg: String): Boolean = favourites.contains(pkg)

    /** Adds to the end of Home's apps row, or takes it off. */
    fun toggle(pkg: String) = if (isFavourite(pkg)) favourites.remove(pkg) else favourites.add(pkg)

    /** The apps manage apps shows under [filter], in the list's own order. */
    fun shown(filter: AppFilter): List<AppEntry> = when (filter) {
        AppFilter.All -> list()
        AppFilter.InRow -> list().filter { isFavourite(it.packageName) }
    }

    /** The filter's pill, with how many apps it holds: "All · 26", "In this row · 6". */
    fun pillLabel(filter: AppFilter): String = when (filter) {
        AppFilter.All -> "All"
        AppFilter.InRow -> "In this row"
    } + " \u00B7 " + shown(filter).size

    /** What an app's menu offers. Nothing for an app that is no longer installed: its menu closes. */
    fun menuFor(pkg: String): List<AppMenuItem> {
        if (catalog.byPackage(pkg) == null) return emptyList()
        return listOf(if (isFavourite(pkg)) AppMenuItem.Remove else AppMenuItem.Add, AppMenuItem.Info)
    }
}

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

/** Which apps manage apps lists. ("Hidden" joins these when hiding arrives.) */
enum class AppFilter { All, InRow }

/** One line of an app's menu in manage apps. */
enum class AppMenuItem(val label: String) {
    Add("Add to Your apps"),
    Remove("Remove from Your apps"),
    Info("App info"),
}

/** The Apps page's "more below" line: shown while a row of apps sits under the [visible] rows that show from [first]. */
fun moreAppsLine(rows: Int, first: Int, visible: Int): String? = if (first + visible < rows) "More apps below" else null
